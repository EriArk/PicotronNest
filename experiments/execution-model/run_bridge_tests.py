"""Run only the owned bridge instrumentation on an explicitly selected x86_64 emulator."""
import argparse
import hashlib
import json
import subprocess
import uuid
from pathlib import Path
from build import ROOT, digest

PACKAGE = 'art.picotronnest.bridgetest'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--mode', choices=['all','normal','abrupt','partial','keyboard','cursor','combined','profile','menu','pointer','mouse','actions','session','keysession','bundle','bundle-disconnect'], default='all')
    parser.add_argument('--artifacts-dir', type=Path, help='Reviewed private fixture build directory')
    args = parser.parse_args()
    def adb(*command, timeout=15):
        return subprocess.run(['adb', '-s', args.serial, *command], check=True,
                              capture_output=True, timeout=timeout).stdout
    if (not args.serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1'
            or adb('shell', 'getprop', 'ro.product.cpu.abi').strip() != b'x86_64'):
        raise ValueError('This runner permits only the owned x86_64 emulator, never physical hardware')
    artifact = args.artifacts_dir.resolve() if args.artifacts_dir else ROOT / '.local/bridge-tests/artifacts'
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
    modes = (['normal', 'abrupt', 'partial', 'keyboard', 'cursor', 'combined', 'profile', 'profile-read', 'menu', 'pointer', 'mouse', 'actions', 'actions-read', 'session', 'keysession', 'bundle', 'bundle-disconnect']
             if args.mode == 'all' else [args.mode, args.mode + '-read'] if args.mode in ('profile','actions') else [args.mode])
    profile_host_pids = {}
    for mode in modes:
        output = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'mode', mode, '-e', 'session', session,
                     PACKAGE + '/art.picotronnest.executionlab.BridgeInstrumentation', timeout=120)
        (evidence / (mode + '-instrument.txt')).write_bytes(output)
        raw = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/bridge-' + mode + '.json')
        (evidence / (mode + '.json')).write_bytes(raw)
        try:
            report = json.loads(raw)
        except (ValueError, UnicodeDecodeError) as error:
            raise ValueError('No valid ' + mode + ' report; instrumentation output: '
                             + output.decode('utf-8', errors='replace')[-2000:]) from error
        if mode in ('keyboard','cursor','combined','profile','profile-read','menu','pointer','mouse','actions','actions-read','session','keysession','bundle','bundle-disconnect') and report.get('session') == session and report.get('screenshot') == mode + '-preview.png':
            screenshot = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/' + mode + '-preview.png')
            if not screenshot.startswith(b'\x89PNG\r\n\x1a\n'):
                raise ValueError('Invalid emulator screenshot')
            (evidence / (mode + '.png')).write_bytes(screenshot)
        if report.get('session') != session or report.get('mode') != mode:
            raise ValueError('No fresh ' + mode + ' report; instrumentation output: '
                             + output.decode('utf-8', errors='replace')[-2000:])
        if report.get('outcome') != 'PASS':
            raise ValueError('Instrumentation failed: ' + str(report.get('failure', report)))
        if report.get('official_runtime') != 'NOT TESTED' or report['launches'] != 1:
            raise ValueError('Incorrect runtime claim or duplicate launch')
        if mode in ('profile','actions'):
            profile_host_pids[mode] = report['host_pid']
        if mode in ('profile-read','actions-read') and (mode[:-5] not in profile_host_pids or report['host_pid'] == profile_host_pids[mode[:-5]]):
            raise ValueError('Profile persistence must be read in a new host process')
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
