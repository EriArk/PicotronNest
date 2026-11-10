"""Build owned SDL observers against a verified previous headless build."""
import json
from host_tools import executable
from pathlib import Path
import subprocess
import zipfile
import shutil

from prepare_headless import HERE, ROOT, digest, fetch, regular_files, run_logged
from prepare_proot import deb_payload


def prepare(destination, llvm, base_apk):
    base = json.loads(base_apk.with_name('build.json').read_text(encoding='utf-8'))
    if digest(base_apk) != base['apk_sha256']:
        raise ValueError('Headless base APK hash mismatch')
    for name in ('host_tools.py', 'prepare_headless.py', 'headless.lock.json', 'runtime-contract.json', 'native/headless_exec.c'):
        path = HERE / name
        if digest(path) != base['source_sha256'].get(str(path.relative_to(ROOT)).replace('\\', '/')):
            raise ValueError('Headless base input changed; rebuild --headless first')
    manifest = base['headless_components']
    files = {}
    notices = ('SDL2-LICENSE.txt', 'GLIBC-COPYRIGHT.txt', 'LINUX-HEADERS-COPYRIGHT.txt',
               'SDL2-EMBEDDED-NOTICES.txt', 'GLIBC-COPYING.txt', 'GLIBC-COPYING.LIB.txt',
               'GLIBC-LICENSES.txt', 'headless-sources.json')
    names = ['assets/headless/' + name for name in manifest['files']]
    names += ['assets/licenses/' + name for name in notices]
    with zipfile.ZipFile(base_apk) as archive:
        for name in names:
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(name))
            files[name] = target
    # Pinned sources and sysroot for the reviewed dummy-audio sample clock patch.
    for item in manifest['inputs']['inputs']:
        name = item['file']
        if name.endswith('.deb'):
            omitted = ('usr/include/linux/netfilter',) if name.startswith('linux-libc-dev_') else ()
            regular_files(deb_payload(fetch(item)), destination / 'sysroot', omitted)
        elif name == 'SDL2-2.32.10.tar.gz':
            regular_files(fetch(item), destination / 'source')
    sysroot = destination / 'sysroot'
    lib = destination / 'assets/headless/lib'
    from patch_audio_clock import build as build_audio_sdl, identity as audio_clock_identity
    clock_identity = audio_clock_identity()
    if manifest.get('audio_clock_patch') != clock_identity:
        patched = build_audio_sdl(destination / 'source/SDL2-2.32.10', sysroot, destination, llvm)
        target = lib / 'libSDL2-2.0.so.0'
        shutil.copyfile(patched, target)
        needed_text = subprocess.run([str(executable(llvm, 'llvm-readelf')), '-d', str(target)],
                                     capture_output=True, text=True, check=True, timeout=10).stdout
        import re
        needed = re.findall(r'\(NEEDED\).*?\[([^\]]+)\]', needed_text)
        if any('lib/' + name not in manifest['files'] for name in needed):
            raise ValueError('Patched SDL dependency closure is incomplete')
        manifest['files']['lib/libSDL2-2.0.so.0'] = {'sha256': digest(target), 'size': target.stat().st_size, 'needed': needed}
    manifest['audio_clock_patch'] = clock_identity
    notice = destination / 'SDL2-PicotronNest-CHANGES.txt'
    notice.write_text('SDL 2.32.10 is modified by PicotronNest: dummy playback uses a fractional sample-count clock, '
                      'including callback work in each period. Original SDL notices remain intact. '
                      'Patch source: experiments/execution-model/patch_audio_clock.py and native/audio_clock.h, '
                      'native/dummy_audio_clock.h in the PicotronNest source archive. Picotron itself is unchanged.\n')
    files['assets/licenses/SDL2-PicotronNest-CHANGES.txt'] = notice
    common = [executable(llvm, 'clang'), '--target=aarch64-linux-gnu', '--sysroot=' + str(sysroot),
              '-nostdlib', '-fuse-ld=lld', '-mno-outline-atomics', '-O2', '-Wall', '-Wextra', '-Werror',
              '-I' + str(destination / 'source/SDL2-2.32.10/include')]
    selected = {'lib/libdesktop_trace.so': 'desktop_trace.c', 'probe/desktop-exec': 'desktop_exec.c',
                'lib/libnetwork_resolver.so': 'network_resolver.c'}
    for name, source in selected.items():
        output = destination / 'assets/headless' / name
        output.parent.mkdir(parents=True, exist_ok=True)
        if name.endswith('.so'):
            options = ['-fPIC', '-shared', '-Wl,-z,defs']
        else:
            options = ['-fPIE', '-pie', sysroot / 'usr/lib/aarch64-linux-gnu/Scrt1.o',
                       sysroot / 'usr/lib/aarch64-linux-gnu/crti.o',
                       '-Wl,--dynamic-linker=/lib/ld-linux-aarch64.so.1']
        run_logged([*common, *options, HERE / 'native' / source, lib / 'libSDL2-2.0.so.0',
                    lib / 'libc.so.6', lib / 'libdl.so.2',
                    *([] if name.endswith('.so') else [sysroot / 'usr/lib/aarch64-linux-gnu/crtn.o']),
                    '-o', output], destination / (source + '.log'))
        elf = subprocess.run([str(executable(llvm, 'llvm-readelf')), '-d', str(output)],
                             capture_output=True, text=True, check=True, timeout=10).stdout
        import re
        needed = re.findall(r'\(NEEDED\).*?\[([^\]]+)\]', elf)
        if any('lib/' + dependency not in manifest['files'] for dependency in needed):
            raise ValueError('Desktop dependency closure is incomplete')
        manifest['files'][name] = {'sha256': digest(output), 'size': output.stat().st_size, 'needed': needed}
        files['assets/headless/' + name] = output
    from prepare_network import prepare as prepare_network
    files.update(prepare_network(destination, llvm, manifest))
    manifest['base_apk_sha256'] = base['apk_sha256']
    manifest_file = destination / 'headless-components.json'
    manifest_file.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    files['assets/headless-components.json'] = manifest_file
    return files, manifest
