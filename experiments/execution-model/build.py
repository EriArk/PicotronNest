"""Build owned fixtures and optional pinned PRoot. No proprietary runtime input."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid
import zipfile


HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent


def run(*args):
    subprocess.run([str(a) for a in args], check=True, timeout=120)


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, default=Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk')
    parser.add_argument('--jdk', type=Path, default=Path(r'C:\Program Files\Android\Android Studio\jbr'))
    parser.add_argument('--linux-loader', action='store_true', help='Include the pinned PRoot guest probe')
    parser.add_argument('--headless', action='store_true', help='Include the ARM64 glibc/SDL probe; runtime is imported separately')
    parser.add_argument('--desktop-base', type=Path, help='Build the desktop observer using a verified headless APK and sibling build.json')
    args = parser.parse_args()
    if args.desktop_base:
        args.headless = True
    if args.headless:
        args.linux_loader = True
    ndk_version = '28.2.13676358'
    ndk = args.sdk / 'ndk' / ndk_version
    llvm = ndk / 'toolchains/llvm/prebuilt/windows-x86_64/bin'
    build_tools = args.sdk / 'build-tools/36.0.0'
    android = args.sdk / 'platforms/android-36/android.jar'
    java, javac, jar, keytool = (args.jdk / 'bin' / (n + '.exe') for n in ('java', 'javac', 'jar', 'keytool'))
    for path in (llvm / 'clang.exe', llvm / 'llvm-readelf.exe', android, java, javac, jar, keytool,
                 build_tools / 'aapt.exe', build_tools / 'lib/d8.jar', ndk / 'NOTICE'):
        if not path.is_file():
            raise SystemExit(f'Required build input missing: {path}')
    sources = sorted((HERE / 'src').rglob('*.java'))
    recorded_sources = [HERE / 'AndroidManifest.xml', HERE / 'native/probe.c', HERE / 'build.py', *sources]
    if args.linux_loader:
        recorded_sources += [HERE / n for n in ['prepare_proot.py', 'proot.lock.json', 'native/linux_guest.c']]
    if args.headless:
        recorded_sources += [HERE / n for n in ['prepare_headless.py', 'headless.lock.json',
                                               'runtime-contract.json', 'native/headless_exec.c']]
    if args.desktop_base:
        recorded_sources += [HERE / n for n in ('prepare_desktop.py', 'native/desktop_exec.c', 'native/desktop_trace.c')]
    source_hashes = {str(p.relative_to(ROOT)).replace('\\', '/'): digest(p) for p in recorded_sources}
    work = ROOT / '.local/execution-model/builds' / uuid.uuid4().hex
    artifacts = ROOT / '.local/execution-model/artifacts'
    for directory in (work / 'classes', work / 'dex', work / 'assets', artifacts):
        directory.mkdir(parents=True, exist_ok=True)
    # NDK CRT/compiler support notices accompany the native fixture.
    shutil.copyfile(ndk / 'NOTICE', work / 'assets/NDK-NOTICE.txt')
    extra_files, components, headless = {}, None, None
    if args.linux_loader:
        from prepare_proot import prepare
        extra_files, components = prepare(work / 'proot')
        components['guests'] = {}
        if args.headless:
            if args.desktop_base:
                from prepare_desktop import prepare as prepare_desktop
                added, headless = prepare_desktop(work / 'headless', llvm, args.desktop_base.resolve())
            else:
                from prepare_headless import prepare as prepare_headless
                added, headless = prepare_headless(work / 'headless', llvm)
            extra_files.update(added)
            extra_files['assets/runtime-contract.json'] = HERE / 'runtime-contract.json'
        for name, file in extra_files.items():
            if name.startswith('assets/'):
                target = work / name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, target)
    natives = {}
    for abi, triple in [('arm64-v8a', 'aarch64-linux-android26'), ('x86_64', 'x86_64-linux-android26')]:
        native = work / abi / 'libexecution_probe.so'
        native.parent.mkdir()
        run(llvm / 'clang.exe', f'--target={triple}', '-fPIE', '-pie',
            '-Wall', '-Wextra', '-Werror', '-O2', '-Wl,-z,max-page-size=16384',
            HERE / 'native/probe.c', '-o', native)
        elf = subprocess.run([str(llvm / 'llvm-readelf.exe'), '-h', '-l', '-d', str(native)],
                             capture_output=True, text=True, check=True, timeout=10).stdout
        (native.parent / 'fixture-elf.txt').write_text(elf, encoding='utf-8')
        natives[abi] = native
        if args.linux_loader:
            guest = work / 'assets/guest' / abi / 'probe'
            guest.parent.mkdir(parents=True)
            run(llvm / 'clang.exe', f'--target={triple}', '-nostdlib', '-static',
                '-fno-builtin', '-fno-stack-protector', '-fno-unwind-tables', '-fno-asynchronous-unwind-tables',
                '-Wall', '-Wextra', '-Werror', '-O2', '-Wl,-e,_start', '-Wl,-z,max-page-size=16384',
                HERE / 'native/linux_guest.c', '-o', guest)
            extra_files[f'assets/guest/{abi}/probe'] = guest
            components['guests'][abi] = digest(guest)
    if components:
        component_file = work / 'assets/linux-components.json'
        component_file.write_text(json.dumps(components, indent=2) + '\n', encoding='utf-8')
        extra_files['assets/linux-components.json'] = component_file
    config = work / 'BuildConfig.java'
    config.write_text('package art.picotronnest.executionlab; final class BuildConfig {'
                      'static final boolean LINUX_LOADER = ' + str(args.linux_loader).lower() + ';'
                      'static final boolean HEADLESS = ' + str(args.headless).lower() + ';'
                      'static final boolean DESKTOP = ' + str(bool(args.desktop_base)).lower() + ';}', encoding='utf-8')
    run(javac, '--release', '8', '-encoding', 'UTF-8', '-Xlint:all,-options', '-Werror', '-classpath', android,
        '-d', work / 'classes', *sources, config)
    run(jar, '--create', '--file', work / 'classes.jar', '-C', work / 'classes', '.')
    run(java, '-cp', build_tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '26',
        '--lib', android, '--output', work / 'dex', work / 'classes.jar')
    unsigned = work / 'unsigned.apk'
    manifest = HERE / 'AndroidManifest.xml'
    if args.linux_loader:
        manifest = work / 'AndroidManifest.xml'
        version = '5' if args.desktop_base else ('3' if args.headless else '2')
        manifest.write_text((HERE / 'AndroidManifest.xml').read_text().replace('versionCode="1"', 'versionCode="' + version + '"')
                            .replace('versionName="0.0.1"', 'versionName="0.0.' + version + '"'), encoding='utf-8')
    run(build_tools / 'aapt.exe', 'package', '-f', '-M', manifest,
        '-A', work / 'assets', '-I', android, '-F', unsigned)
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(work / 'dex/classes.dex', 'classes.dex')
        for abi, native in natives.items():
            archive.write(native, f'lib/{abi}/libexecution_probe.so')
        for name, file in extra_files.items():
            if name.startswith('lib/'):
                archive.write(file, name)
    aligned = work / 'aligned.apk'
    run(build_tools / 'zipalign.exe', '-f', '-P', '16', '4', unsigned, aligned)
    key = artifacts / 'execution-lab-debug.keystore'
    if not key.exists():
        run(keytool, '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
            '-alias', 'execution-lab', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
            '-dname', 'CN=PicotronNest Execution Lab')
    apk = artifacts / 'picotronnest-execution-lab.apk'
    run(java, '-jar', build_tools / 'lib/apksigner.jar', 'sign', '--ks', key,
        '--ks-key-alias', 'execution-lab', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
        '--out', apk, aligned)
    run(java, '-jar', build_tools / 'lib/apksigner.jar', 'verify', apk)
    allowed = {'AndroidManifest.xml', 'assets/NDK-NOTICE.txt', 'classes.dex'}
    allowed.update(f'lib/{abi}/libexecution_probe.so' for abi in natives)
    allowed.update(extra_files)
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        payloads = {n for n in names if not n.startswith('META-INF/')}
        if len(names) != len(set(names)) or payloads != allowed:
            raise SystemExit(f'Unexpected APK payloads: {sorted(payloads)}')
        for abi, native in natives.items():
            if hashlib.sha256(archive.read(f'lib/{abi}/libexecution_probe.so')).hexdigest() != digest(native):
                raise SystemExit('Packaged fixture hash mismatch')
        for name, file in extra_files.items():
            if hashlib.sha256(archive.read(name)).hexdigest() != digest(file):
                raise SystemExit(f'Packaged component hash mismatch: {name}')
    if any(digest(ROOT / name) != value for name, value in source_hashes.items()):
        raise SystemExit('Build inputs changed during compilation; rebuild before installation')
    metadata = {'ndk': ndk_version, 'build_tools': '36.0.0', 'compile_sdk': 36, 'target_sdk': 36,
                'apk_sha256': digest(apk), 'fixture_sha256': {abi: digest(p) for abi, p in natives.items()},
                'source_sha256': source_hashes}
    if components:
        metadata['linux_components'] = components
    if headless:
        metadata['headless_components'] = headless
        metadata['runtime_contract'] = json.loads((HERE / 'runtime-contract.json').read_text(encoding='utf-8'))
    if args.desktop_base:
        metadata['desktop_probe'] = True
        metadata['desktop_live'] = True
    (artifacts / 'build.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(metadata, indent=2))
    print(f'Local APK: {apk}')


if __name__ == '__main__':
    main()
