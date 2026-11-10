"""Install/run the owned screenshot/community fixture on an explicitly selected emulator only."""
import argparse
import json
from pathlib import Path
import subprocess
import uuid
from build_screenshot_tests import ROOT, digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--mode', choices=['screenshot', 'community'], default='screenshot')
    args = parser.parse_args()

    def adb(*cmd, timeout=30):
        return subprocess.run([args.adb, '-s', args.serial, *map(str, cmd)], capture_output=True, check=True, timeout=timeout).stdout

    if not args.serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1':
        raise ValueError('Select a dedicated owned emulator, never physical hardware')
    artifacts = ROOT / '.local/screenshot-tests/artifacts'
    build = json.loads((artifacts / 'build.json').read_text())
    apk = artifacts / 'screenshot-tests.apk'
    if digest(apk) != build['apk_sha256'] or any(digest(ROOT / name) != sha for name, sha in build['source_sha256'].items()):
        raise ValueError('APK or source changed; rebuild before testing')
    package = 'art.picotronnest.screenshottest'
    adb('install', '-r', apk)
    installed = adb('shell', 'pm', 'path', package).decode().strip()
    if not installed.startswith('package:/data/app/') or '\n' in installed:
        raise ValueError('Unexpected installed package layout')
    import hashlib
    if hashlib.sha256(adb('exec-out', 'cat', installed.removeprefix('package:'))).hexdigest() != build['apk_sha256']:
        raise ValueError('Installed APK mismatch')
    session = str(uuid.uuid4())
    evidence = ROOT / '.local/screenshot-tests/evidence' / session
    evidence.mkdir(parents=True)
    (evidence / 'build.json').write_text(json.dumps(build, indent=2))
    instrument = 'CommunityInstrumentation' if args.mode == 'community' else 'ScreenshotInstrumentation'
    output = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'session', session,
                 package + '/art.picotronnest.executionlab.' + instrument, timeout=120)
    (evidence / 'instrument.txt').write_bytes(output)
    raw = adb('exec-out', 'run-as', package, 'cat', 'files/' + args.mode + '-report.json')
    (evidence / 'report.json').write_bytes(raw)
    report = json.loads(raw)
    if report.get('session') != session or report.get('outcome') != 'PASS' or report.get('official_runtime') != 'NOT TESTED':
        raise ValueError(report)
    source = 'community-preview.png' if args.mode == 'community' else 'saved.png'
    (evidence / ('community-preview.png' if args.mode == 'community' else 'owned-frame.png')).write_bytes(
        adb('exec-out', 'run-as', package, 'cat', 'files/' + source))
    print(json.dumps(report, indent=2))
    print(evidence)


if __name__ == '__main__':
    main()
