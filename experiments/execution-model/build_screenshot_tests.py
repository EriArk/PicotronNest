"""Build a Java-only Android screenshot fixture; no native or proprietary payloads."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import uuid
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', required=True, type=Path)
    parser.add_argument('--android-jar', required=True, type=Path)
    parser.add_argument('--build-tools', required=True, type=Path)
    args = parser.parse_args()
    suffix = '.exe' if os.name == 'nt' else ''
    java, javac, jar, keytool = [args.jdk / 'bin' / (name + suffix) for name in ('java', 'javac', 'jar', 'keytool')]
    bt = args.build_tools
    work = ROOT / '.local/screenshot-tests/builds' / uuid.uuid4().hex
    artifacts = ROOT / '.local/screenshot-tests/artifacts'
    for path in (work / 'classes', work / 'dex', artifacts):
        path.mkdir(parents=True, exist_ok=True)

    def run(*cmd):
        subprocess.run(list(map(str, cmd)), check=True, timeout=120)

    sources = sorted((HERE / 'src').rglob('*.java')) + [HERE / 'tests/android' / (name + '.java') for name in
                ('ScreenshotTestActivity', 'ScreenshotDocuments', 'ScreenshotInstrumentation')]
    hashes = {p.relative_to(ROOT).as_posix(): digest(p) for p in sources + [Path(__file__)]}
    config = work / 'BuildConfig.java'
    config.write_text('package art.picotronnest.executionlab; final class BuildConfig {'
                      'static final boolean DESKTOP=true, HEADLESS=false, LINUX_LOADER=false;}', encoding='utf-8')
    run(javac, '--release', '8', '-encoding', 'UTF-8', '-Xlint:all,-options', '-Werror',
        '-classpath', args.android_jar, '-d', work / 'classes', *sources, config)
    run(jar, '--create', '--file', work / 'classes.jar', '-C', work / 'classes', '.')
    run(java, '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '26',
        '--lib', args.android_jar, '--output', work / 'dex', work / 'classes.jar')
    manifest = work / 'AndroidManifest.xml'
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="art.picotronnest.screenshottest" android:versionCode="1" android:versionName="0.0.1">
        <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36" />
        <application android:label="Owned screenshot tests - no Picotron" android:debuggable="true"
            android:allowBackup="false" android:theme="@android:style/Theme.Material.Light.NoActionBar">
            <activity android:name="art.picotronnest.executionlab.ScreenshotTestActivity" android:exported="false" />
            <activity android:name="art.picotronnest.executionlab.ScreenshotActivity" android:exported="false" />
            <provider android:name="art.picotronnest.executionlab.ScreenshotDocuments"
                android:authorities="art.picotronnest.screenshottest.documents" android:exported="false" />
        </application>
        <instrumentation android:name="art.picotronnest.executionlab.ScreenshotInstrumentation"
            android:targetPackage="art.picotronnest.screenshottest" />
    </manifest>''', encoding='utf-8')
    unsigned = work / 'unsigned.apk'
    run(bt / ('aapt' + suffix), 'package', '-f', '-M', manifest, '-I', args.android_jar, '-F', unsigned)
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(work / 'dex/classes.dex', 'classes.dex')
    aligned = work / 'aligned.apk'
    run(bt / ('zipalign' + suffix), '-f', '4', unsigned, aligned)
    key = artifacts / 'screenshot-debug.keystore'
    if not key.exists():
        run(keytool, '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
            '-alias', 'screenshot-tests', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
            '-dname', 'CN=PicotronNest Owned Screenshot Tests')
    apk = artifacts / 'screenshot-tests.apk'
    run(java, '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', key, '--ks-key-alias', 'screenshot-tests',
        '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', apk, aligned)
    run(java, '-jar', bt / 'lib/apksigner.jar', 'verify', apk)
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or {n for n in names if not n.startswith('META-INF/')} != {'AndroidManifest.xml', 'classes.dex'}:
            raise ValueError('Unexpected screenshot test payload')
    if any(digest(ROOT / name) != sha for name, sha in hashes.items()):
        raise ValueError('Sources changed during build; rebuild')
    (artifacts / 'build.json').write_text(json.dumps({'apk_sha256': digest(apk), 'source_sha256': hashes,
        'android_jar_sha256': digest(args.android_jar), 'target_sdk': 36, 'minimum_sdk': 26}, indent=2) + '\n')
    print(apk)


if __name__ == '__main__':
    main()
