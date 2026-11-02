"""Collect owned AudioTrack buffer/lifecycle evidence on the dedicated x86_64 emulator."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import uuid
from build import ROOT, digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', default=str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'))
    args = parser.parse_args()

    def adb(*command, timeout=30):
        return subprocess.run([args.adb, '-s', args.serial, *command], check=True, capture_output=True, timeout=timeout).stdout

    if (not args.serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1'
            or adb('shell', 'getprop', 'ro.product.cpu.abi').strip() != b'x86_64'):
        raise ValueError('Use the owned x86_64 emulator')
    package = 'art.picotronnest.bridgetest'
    build = json.loads((ROOT / '.local/bridge-tests/artifacts/build.json').read_text())
    if any(digest(ROOT / name) != value for name, value in build['source_sha256'].items()):
        raise ValueError('Rebuild changed sources')
    path = adb('shell', 'pm', 'path', package).decode().strip()
    if not path.startswith('package:/data/app/') or '\n' in path:
        raise ValueError('Unexpected package path')
    if hashlib.sha256(adb('exec-out', 'cat', path.removeprefix('package:'))).hexdigest() != build['apk_sha256']:
        raise ValueError('Installed APK does not match build')
    session = str(uuid.uuid4())
    evidence = ROOT / '.local/audio-tests' / session
    evidence.mkdir(parents=True)
    (evidence / 'build.json').write_text(json.dumps(build, indent=2))
    (evidence / 'device.txt').write_bytes(adb('shell', 'getprop', 'ro.build.fingerprint') + adb('shell', 'getenforce'))
    output = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'session', session,
                 package + '/art.picotronnest.executionlab.AudioInstrumentation', timeout=90)
    (evidence / 'instrument.txt').write_bytes(output)
    raw = adb('exec-out', 'run-as', package, 'cat', 'files/audio-test.json')
    (evidence / 'report.json').write_bytes(raw)
    report = json.loads(raw)
    if report.get('session') != session or report.get('outcome') != 'PASS':
        raise ValueError('Audio fixture failed: ' + str(report))
    if report.get('official_runtime') != 'NOT TESTED' or report.get('audible_quality') != 'NOT TESTED':
        raise ValueError('Fixture must not claim official gameplay or listening acceptance')
    (evidence / 'settings.png').write_bytes(adb('exec-out', 'run-as', package, 'cat', 'files/audio-settings.png'))
    print('PASS: owned PCM, live buffer modes, background discard and resume. Evidence: ' + str(evidence))


if __name__ == '__main__':
    main()
