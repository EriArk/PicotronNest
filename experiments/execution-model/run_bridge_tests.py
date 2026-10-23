"""Run only the owned bridge instrumentation on an explicitly selected x86_64 emulator."""
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
    def adb(*command, timeout=15):
        return subprocess.run(['adb', '-s', args.serial, *command], check=True,
                              capture_output=True, timeout=timeout).stdout
    if (not args.serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1'
            or adb('shell', 'getprop', 'ro.product.cpu.abi').strip() != b'x86_64'):
        raise ValueError('This runner permits only the owned x86_64 emulator, never physical hardware')
    artifact = ROOT / '.local/bridge-tests/artifacts'
    build = json.loads((artifact / 'build.json').read_text())
    if digest(artifact / 'bridge-tests.apk') != build['apk_sha256']:
        raise ValueError('Build APK hash changed')
    for name, expected in build['source_sha256'].items():
        if digest(ROOT / name) != expected:
            raise ValueError('Rebuild changed source: ' + name)
    paths = adb('shell', 'pm', 'path', PACKAGE).decode().strip().splitlines()
    if len(paths) != 1 or not paths[0].startswith('package:/data/app/'):
        raise ValueError('Install the reviewed bridge APK first')
    installed = paths[0].removeprefix('package:')
    if hashlib.sha256(adb('exec-out', 'cat', installed)).hexdigest() != build['apk_sha256']:
        raise ValueError('Installed bridge APK is not the current build')
    session = str(uuid.uuid4())
    evidence = ROOT / '.local/bridge-tests/evidence' / session
    evidence.mkdir(parents=True)
    (evidence / 'build.json').write_text(json.dumps(build, indent=2), encoding='utf-8')
    device = {key: adb('shell', 'getprop', key).decode().strip() for key in
              ['ro.product.model', 'ro.build.version.release', 'ro.build.version.sdk', 'ro.product.cpu.abi']}
    device['selinux'] = adb('shell', 'getenforce').decode().strip()
    (evidence / 'device.json').write_text(json.dumps(device, indent=2), encoding='utf-8')
    for mode in ['normal', 'abrupt', 'partial']:
        output = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'mode', mode, '-e', 'session', session,
                     PACKAGE + '/art.picotronnest.executionlab.BridgeInstrumentation', timeout=120)
        (evidence / (mode + '-instrument.txt')).write_bytes(output)
        raw = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/bridge-' + mode + '.json')
        (evidence / (mode + '.json')).write_bytes(raw)
        report = json.loads(raw)
        if report.get('session') != session or report.get('mode') != mode or report.get('outcome') != 'PASS':
            raise ValueError('Instrumentation failed: ' + str(report.get('failure', report)))
        if report.get('official_runtime') != 'NOT TESTED' or report['launches'] != 1:
            raise ValueError('Incorrect runtime claim or duplicate launch')
        # Require numeric UIDs so the same-UID process check cannot silently miss names.
        census = adb('shell', 'ps', '-A', '-n', '-o', 'UID,PID,NAME').decode()
        owned = [line for line in census.splitlines()[1:] if line.split() and line.split()[0] == str(report['host_uid'])]
        (evidence / (mode + '-census.txt')).write_text('\n'.join(owned) + '\n', encoding='utf-8')
        if any(int(line.split()[1]) != report['host_pid'] for line in owned):
            raise ValueError('Unexpected surviving process in bridge-test UID')
        print(mode + ': PASS; checks=' + str(len(report['checks'])) + '; child=' + str(report['native_pid'])
              + '; exit=' + str(report['exit_code']))
    print('Evidence: ' + str(evidence))


if __name__ == '__main__':
    main()
