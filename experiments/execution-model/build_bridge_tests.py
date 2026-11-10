"""Build an isolated Android instrumentation fixture; no official runtime."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import uuid
import zipfile
from build import HERE, ROOT, digest, run
from host_tools import executable, ndk_host, default_sdk, default_jdk
from prepare_headless import fetch, regular_files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--abi', choices=['x86_64', 'arm64-v8a'], default='x86_64')
    parser.add_argument('--sdk', type=Path, default=default_sdk())
    parser.add_argument('--jdk', type=Path, default=default_jdk())
    parser.add_argument('--artifacts-dir', type=Path)
    args = parser.parse_args()
    if args.jdk is None:
        parser.error('Provide --jdk, set JAVA_HOME, or put javac on PATH')
    abi = args.abi
    target = 'aarch64' if abi == 'arm64-v8a' else 'x86_64'
    sdk = args.sdk.resolve()
    jdk = args.jdk.resolve() / 'bin'
    ndk = sdk / 'ndk/28.2.13676358'
    llvm = ndk / 'toolchains/llvm/prebuilt' / ndk_host() / 'bin'
    bt = sdk / 'build-tools/36.0.0'
    android = sdk / 'platforms/android-36/android.jar'
    work = ROOT / '.local/bridge-tests/builds' / uuid.uuid4().hex
    artifacts = args.artifacts_dir.resolve() if args.artifacts_dir else ROOT / '.local/bridge-tests/artifacts'
    for directory in [work / 'classes', work / 'dex', work / 'assets', artifacts]:
        directory.mkdir(parents=True, exist_ok=True)
    sources = sorted((HERE / 'src').rglob('*.java')) + sorted((HERE / 'tests/android').glob('*.java')) + [HERE / 'tests/RuntimeStoreChecks.java']
    inputs = sources + [HERE / n for n in ['build_bridge_tests.py', 'build.py', 'host_tools.py', 'tests/bridge_fixture.c', 'tests/ordered_selftest.h',
                                          'native/pointer_protocol.h', 'native/pointer_sdl.h', 'native/keyboard_protocol.h', 'native/keyboard_sdl.h', 'native/keyboard_channel.h', 'native/ordered_input.h', 'native/gamepad_protocol.h',
                                          'prepare_headless.py', 'prepare_proot.py', 'headless.lock.json']]
    hashes = {p.relative_to(ROOT).as_posix(): digest(p) for p in inputs}
    sdl_input = next(item for item in json.loads((HERE / 'headless.lock.json').read_text())['inputs']
                     if item['file'] == 'SDL2-2.32.10.tar.gz')
    regular_files(fetch(sdl_input), work / 'source')
    sdl = work / 'source/SDL2-2.32.10'
    shutil.copyfile(sdl / 'LICENSE.txt', work / 'assets/SDL2-LICENSE.txt')
    native = work / 'libbridge_fixture.so'
    run(executable(llvm, 'clang'), f'--target={target}-linux-android26', '-fPIE', '-pie', '-O2',
        '-Wall', '-Wextra', '-Werror', '-I' + str(sdl / 'include'),
        '-Wl,-z,max-page-size=16384', HERE / 'tests/bridge_fixture.c', '-o', native)
    elf = subprocess.run([str(executable(llvm, 'llvm-readelf')), '-d', str(native)],
                         check=True, capture_output=True, text=True, timeout=10).stdout
    (work / 'fixture-elf.txt').write_text(elf, encoding='utf-8')
    import re
    if set(re.findall(r'\(NEEDED\).*?\[([^\]]+)\]', elf)) - {'libc.so', 'libdl.so'}:
        raise ValueError('Unexpected fixture dependency')
    shutil.copyfile(ndk / 'NOTICE', work / 'assets/NDK-NOTICE.txt')
    config = work / 'BuildConfig.java'
    config.write_text('package art.picotronnest.executionlab; final class BuildConfig {'
                      'static final boolean DESKTOP=true, HEADLESS=false, LINUX_LOADER=false;}', encoding='utf-8')
    run(executable(jdk, 'javac'), '--release', '8', '-encoding', 'UTF-8', '-Xlint:all,-options', '-Werror',
        '-classpath', android, '-d', work / 'classes', *sources, config)
    run(executable(jdk, 'jar'), '--create', '--file', work / 'classes.jar', '-C', work / 'classes', '.')
    run(executable(jdk, 'java'), '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '26',
        '--lib', android, '--output', work / 'dex', work / 'classes.jar')
    manifest = work / 'AndroidManifest.xml'
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="art.picotronnest.bridgetest" android:versionCode="1" android:versionName="0.0.1">
        <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36" />
        <uses-permission android:name="android.permission.INTERNET" />
        <application android:label="Owned bridge tests - no Picotron" android:debuggable="true"
            android:allowBackup="false" android:extractNativeLibs="true"
            android:theme="@android:style/Theme.Material.Light.NoActionBar">
            <activity android:name="art.picotronnest.executionlab.BridgeTestActivity" android:exported="false" />
            <activity android:name="art.picotronnest.executionlab.StorageSetupActivity" android:exported="false" />
            <activity android:name="art.picotronnest.executionlab.AudioTestActivity" android:exported="false" />
            <activity android:name="art.picotronnest.executionlab.TouchGamepadTestActivity" android:exported="false" />
            <provider android:name="art.picotronnest.executionlab.StorageDocuments" android:authorities="art.picotronnest.bridgetest.storage" android:exported="false" />
        </application>
        <instrumentation android:name="art.picotronnest.executionlab.BridgeInstrumentation"
            android:targetPackage="art.picotronnest.bridgetest" />
        <instrumentation android:name="art.picotronnest.executionlab.StorageInstrumentation"
            android:targetPackage="art.picotronnest.bridgetest" />
        <instrumentation android:name="art.picotronnest.executionlab.QueueInstrumentation"
            android:targetPackage="art.picotronnest.bridgetest" />
        <instrumentation android:name="art.picotronnest.executionlab.AudioInstrumentation"
            android:targetPackage="art.picotronnest.bridgetest" />
        <instrumentation android:name="art.picotronnest.executionlab.TouchGamepadInstrumentation"
            android:targetPackage="art.picotronnest.bridgetest" />
        <instrumentation android:name="art.picotronnest.executionlab.NetworkInstrumentation"
            android:targetPackage="art.picotronnest.bridgetest" />
    </manifest>''', encoding='utf-8')
    unsigned = work / 'unsigned.apk'
    run(executable(bt, 'aapt'), 'package', '-f', '-M', manifest, '-A', work / 'assets', '-I', android, '-F', unsigned)
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(work / 'dex/classes.dex', 'classes.dex')
        archive.write(native, f'lib/{abi}/libbridge_fixture.so')
    aligned = work / 'aligned.apk'
    run(executable(bt, 'zipalign'), '-f', '-P', '16', '4', unsigned, aligned)
    apk = artifacts / 'bridge-tests.apk'
    key = artifacts / 'bridge-tests-debug.keystore'
    if not key.exists():
        run(executable(jdk, 'keytool'), '-genkeypair', '-keystore', key,
            '-storepass', 'android', '-keypass', 'android', '-alias', 'execution-lab',
            '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
            '-dname', 'CN=PicotronNest Owned Bridge Tests')
    run(executable(jdk, 'java'), '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', key, '--ks-key-alias', 'execution-lab',
        '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', apk, aligned)
    run(executable(jdk, 'java'), '-jar', bt / 'lib/apksigner.jar', 'verify', apk)
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or {n for n in names if not n.startswith('META-INF/')} != {
                'AndroidManifest.xml', 'classes.dex', 'assets/NDK-NOTICE.txt', 'assets/SDL2-LICENSE.txt', f'lib/{abi}/libbridge_fixture.so'}:
            raise ValueError('Unexpected bridge APK payload')
        import hashlib
        assert hashlib.sha256(archive.read(f'lib/{abi}/libbridge_fixture.so')).hexdigest() == digest(native)
    if any(digest(ROOT / name) != value for name, value in hashes.items()):
        raise ValueError('Sources changed while building')
    metadata = dict(build_host=ndk_host(), compiler_sha256=digest(executable(llvm, 'clang')), abi=abi, apk_sha256=digest(apk), native_sha256=digest(native), source_sha256=hashes,
                    target_sdk=36, official_runtime='NOT INCLUDED', sdl_headers=sdl_input,
                    native_needed=sorted(re.findall(r'\(NEEDED\).*?\[([^\]]+)\]', elf)))
    (artifacts / 'build.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(metadata, indent=2))


if __name__ == '__main__':
    main()
