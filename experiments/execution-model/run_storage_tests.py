"""Collect the owned Android storage workflow across fresh host processes; emulator only."""
import argparse
import hashlib
import json
import subprocess
import uuid
from build import ROOT, digest

PACKAGE = 'art.picotronnest.bridgetest'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()

    def adb(*command, timeout=20):
        return subprocess.run(['adb', '-s', args.serial, *command], check=True, capture_output=True, timeout=timeout).stdout

    if (not args.serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1'
            or adb('shell', 'getprop', 'ro.product.cpu.abi').strip() != b'x86_64'):
        raise ValueError('Only the owned x86_64 emulator is permitted')
    build = json.loads((ROOT / '.local/bridge-tests/artifacts/build.json').read_text())
    if any(digest(ROOT / name) != value for name, value in build['source_sha256'].items()):
        raise ValueError('Rebuild changed sources')
    path = adb('shell', 'pm', 'path', PACKAGE).decode().strip()
    if not path.startswith('package:/data/app/') or '\n' in path:
        raise ValueError('Unexpected installed package path')
    if hashlib.sha256(adb('exec-out', 'cat', path.removeprefix('package:'))).hexdigest() != build['apk_sha256']:
        raise ValueError('Installed APK does not match build')
    session = str(uuid.uuid4())
    evidence = ROOT / '.local/storage-tests/evidence' / session
    evidence.mkdir(parents=True)
    (evidence / 'build.json').write_text(json.dumps(build, indent=2))
    (evidence / 'device.txt').write_bytes(adb('shell', 'getprop', 'ro.build.fingerprint') + adb('shell', 'getenforce'))
    hosts = set()
    for mode in ('write', 'read', 'recovery'):
        output = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'session', session, '-e', 'mode', mode,
                     PACKAGE + '/art.picotronnest.executionlab.StorageInstrumentation', timeout=120)
        (evidence / (mode + '-instrument.txt')).write_bytes(output)
        raw = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/storage-' + mode + '.json')
        (evidence / (mode + '.json')).write_bytes(raw)
        report = json.loads(raw)
        if report.get('session') != session or report.get('mode') != mode or report.get('outcome') != 'PASS':
            raise ValueError('Storage scenario failed: ' + str(report))
        if report.get('official_runtime') != 'NOT TESTED' or report['host_pid'] in hosts:
            raise ValueError('Invalid runtime claim or host process reuse')
        hosts.add(report['host_pid'])
        census = adb('shell', 'ps', '-A', '-n', '-o', 'UID,PID,NAME').decode()
        owned = [line for line in census.splitlines()[1:] if line.split() and line.split()[0] == str(report['host_uid'])]
        if any(int(line.split()[1]) != report['host_pid'] for line in owned):
            raise ValueError('Surviving owned child')
        (evidence / (mode + '-census.txt')).write_text('\n'.join(owned))
        image = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/storage-' + mode + '.png')
        if not image.startswith(b'\x89PNG\r\n\x1a\n'):
            raise ValueError('Invalid screenshot')
        (evidence / (mode + '.png')).write_bytes(image)
        print(mode + ': PASS; child=' + str(report['native_pid']) + '; exit=' + str(report['exit_code']))
    print('Evidence: ' + str(evidence))


if __name__ == '__main__':
    main()
