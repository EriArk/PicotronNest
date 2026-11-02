"""Build a minimal Linux ARM64 SDL2 and pin its glibc closure, without Picotron."""
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import tarfile
import urllib.request

from prepare_proot import deb_payload

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
CACHE = ROOT / '.local/headless/downloads'
DISABLED = ('ALSA ARTS DBUS DIRECTFB DISKAUDIO ESD FUSIONSOUND HIDAPI_JOYSTICK IBUS JACK KMSDRM '
            'LIBSAMPLERATE LIBUDEV NAS OFFSCREEN OPENGL OPENGLES OSS PIPEWIRE PULSEAUDIO '
            'RPI SNDIO VIVANTE VULKAN WAYLAND X11 HAPTIC SENSOR TEST TESTS STATIC CCACHE RPATH').split()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fetch(item):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / item['file']
    if path.exists():
        data = path.read_bytes()
    else:
        with urllib.request.urlopen(item['url'], timeout=30) as response:
            data = response.read(32 * 1024 * 1024 + 1)
    if len(data) > 32 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != item['sha256']:
        raise ValueError('Headless input checksum/size mismatch: ' + item['file'])
    path.write_bytes(data)
    return data


def safe_name(name):
    name = name.removeprefix('./')
    path = PurePosixPath(name)
    if (not name or path.is_absolute() or '..' in path.parts or '\\' in name or ':' in name
            or any(ord(c) < 32 for c in name)):
        raise ValueError('Unsafe source archive path')
    return name


def regular_files(data, destination, omit_prefixes=()):
    """Only fixed pinned inputs: copy regular files, never materialize archive links."""
    seen, total = set(), 0
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:*') as archive:
        for member in archive:
            if member.isdir():
                continue
            name = safe_name(member.name)
            if any(name.startswith(prefix) for prefix in omit_prefixes):
                continue
            key = name.casefold()
            if key in seen:
                raise ValueError('Duplicate source path')
            seen.add(key)
            if len(seen) > 20000:
                raise ValueError('Too many source entries')
            if not member.isfile():
                # Debian development symlinks are deliberately omitted; link exact .so files.
                if member.issym() or member.islnk():
                    continue
                raise ValueError('Special source entry')
            total += member.size
            if member.size > 32 * 1024 * 1024 or total > 256 * 1024 * 1024:
                raise ValueError('Oversize source archive')
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.extractfile(member) as stream:
                target.write_bytes(stream.read())


def run_logged(command, log):
    with log.open('w', encoding='utf-8') as output:
        result = subprocess.run([str(x) for x in command], stdout=output, stderr=subprocess.STDOUT, timeout=300)
    if result.returncode:
        raise RuntimeError(f'Build failed; inspect {log}')


def prepare(destination, llvm):
    destination.mkdir(parents=True, exist_ok=True)
    lock = json.loads((HERE / 'headless.lock.json').read_text(encoding='utf-8'))
    inputs = {item['file']: fetch(item) for item in lock['inputs']}
    sysroot = destination / 'sysroot'
    for name, data in inputs.items():
        if name.endswith('.deb'):
            # Unused netfilter headers contain case aliases on Windows; exclude that subsystem explicitly.
            omitted = ('usr/include/linux/netfilter',) if name.startswith('linux-libc-dev_') else ()
            regular_files(deb_payload(data), sysroot, omitted)
    regular_files(inputs['SDL2-2.32.10.tar.gz'], destination / 'source')
    source = destination / 'source/SDL2-2.32.10'
    lib = sysroot / 'lib/aarch64-linux-gnu'
    # Use exact library filenames; no host libraries or symlink privileges required.
    libraries = ' '.join((lib / name).as_posix() for name in ('libc.so.6', 'libm.so.6', 'libdl.so.2', 'libpthread.so.0'))
    libraries += ' ' + (sysroot / 'usr/lib/aarch64-linux-gnu/libc_nonshared.a').as_posix()
    toolchain = destination / 'toolchain.cmake'
    toolchain.write_text(f'''set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER "{llvm.as_posix()}/clang.exe")
set(CMAKE_C_COMPILER_TARGET aarch64-linux-gnu)
set(CMAKE_SYSROOT "{sysroot.as_posix()}")
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
set(CMAKE_C_FLAGS_INIT "-fPIC -mno-outline-atomics")
set(CMAKE_SHARED_LINKER_FLAGS_INIT "-nostdlib -fuse-ld=lld -Wl,-z,defs {libraries}")
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)
''', encoding='utf-8')
    build = destination / 'sdl-build'
    run_logged(['cmake', '-S', source, '-B', build, '-G', 'Ninja',
                '-DCMAKE_TOOLCHAIN_FILE=' + str(toolchain), '-DCMAKE_BUILD_TYPE=Release',
                '-DSDL_SHARED=ON', '-DSDL_JOYSTICK=ON', '-DSDL_HIDAPI=ON',
                '-DSDL_VIRTUAL_JOYSTICK=ON', *['-DSDL_' + x + '=OFF' for x in DISABLED]], destination / 'configure.log')
    run_logged(['cmake', '--build', build, '--target', 'SDL2', '--parallel', '8'], destination / 'compile.log')
    sdl = build / 'libSDL2-2.0.so.0.3200.10'
    guest = destination / 'headless-exec'
    run_logged([llvm / 'clang.exe', '--target=aarch64-linux-gnu', '--sysroot=' + str(sysroot),
                '-fPIE', '-pie', '-nostdlib', '-fuse-ld=lld', '-mno-outline-atomics', '-O2',
                '-Wall', '-Wextra', '-Werror', '-I' + str(source / 'include'),
                sysroot / 'usr/lib/aarch64-linux-gnu/Scrt1.o',
                sysroot / 'usr/lib/aarch64-linux-gnu/crti.o', HERE / 'native/headless_exec.c',
                sdl, lib / 'libc.so.6', sysroot / 'usr/lib/aarch64-linux-gnu/crtn.o',
                '-Wl,--dynamic-linker=/lib/ld-linux-aarch64.so.1', '-o', guest], destination / 'guest-build.log')
    selected = {'lib/' + name: lib / name for name in
                ('ld-linux-aarch64.so.1', 'libc.so.6', 'libm.so.6', 'libdl.so.2', 'libpthread.so.0')}
    selected.update({'lib/libSDL2-2.0.so.0': sdl, 'probe/headless-exec': guest})
    manifest = {'schema': 1, 'abi': 'arm64-v8a', 'files': {}, 'inputs': lock}
    manifest['tools'] = {}
    for name, command in [('cmake', ['cmake', '--version']), ('ninja', ['ninja', '--version']),
                          ('clang', [str(llvm / 'clang.exe'), '--version'])]:
        manifest['tools'][name] = subprocess.run(command, capture_output=True, text=True,
                                                check=True, timeout=10).stdout.splitlines()[0]
    manifest['sdl_disabled'] = DISABLED
    files = {}
    for name, path in selected.items():
        output = subprocess.run([str(llvm / 'llvm-readelf.exe'), '-h', '-l', '-d', str(path)],
                                capture_output=True, text=True, check=True, timeout=10).stdout
        needed = re.findall(r'\(NEEDED\).*?\[([^\]]+)\]', output)
        if 'AArch64' not in output or any('lib/' + n not in selected for n in needed):
            raise ValueError('Wrong ABI or unclosed dependency graph: ' + name)
        manifest['files'][name] = {'sha256': digest(path), 'size': path.stat().st_size, 'needed': needed}
        files['assets/headless/' + name] = path
    notices = {'SDL2-LICENSE.txt': (source / 'LICENSE.txt').read_bytes(),
               'GLIBC-COPYRIGHT.txt': (sysroot / 'usr/share/doc/libc6/copyright').read_bytes(),
               'LINUX-HEADERS-COPYRIGHT.txt': (sysroot / 'usr/share/doc/linux-libc-dev/copyright').read_bytes()}
    # SDL also carries permissive third-party code (e.g. Sun fdlibm, CC0 strtok).
    # Preserve embedded copyright/license comment blocks, including header notices.
    embedded = {}
    for folder in ('src', 'include'):
        for path in sorted((source / folder).rglob('*')):
            if path.suffix not in ('.c', '.h'):
                continue
            for comment in re.findall(r'/\*.*?\*/', path.read_text(encoding='utf-8', errors='replace'), re.S):
                if re.search(r'copyright|permission|licen[cs]e|public domain', comment, re.I):
                    embedded.setdefault(comment, str(path.relative_to(source)).replace('\\', '/'))
    notices['SDL2-EMBEDDED-NOTICES.txt'] = '\n\n'.join(name + '\n' + text for text, name in embedded.items()).encode('utf-8')
    with tarfile.open(fileobj=io.BytesIO(inputs['glibc_2.36.orig.tar.xz'])) as archive:
        for name in ('COPYING', 'COPYING.LIB', 'LICENSES'):
            notices['GLIBC-' + name + '.txt'] = archive.extractfile('glibc-2.36/' + name).read()
    notices['headless-sources.json'] = json.dumps(lock, indent=2).encode('utf-8')
    for name, data in notices.items():
        path = destination / 'notices' / name
        path.parent.mkdir(exist_ok=True)
        path.write_bytes(data)
        files['assets/licenses/' + name] = path
    manifest_path = destination / 'headless-components.json'
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    files['assets/headless-components.json'] = manifest_path
    return files, manifest
