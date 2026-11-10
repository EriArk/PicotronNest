"""Check owned import failures and real process death, only on an explicit x86_64 emulator."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import uuid
from build import ROOT, digest

PACKAGE = 'art.picotronnest.bridgetest'
MODES = ('baseline', 'copy-crash', 'copy-read', 'claim-crash', 'claim-read', 'publish-crash', 'publish-read')
BARRIERS = {'copy-crash': 'provider_copy_in_progress', 'claim-crash': 'queued_import_claimed',
            'publish-crash': 'queued_import_published'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--artifacts-dir', type=Path, required=True)
    args = parser.parse_args()

    def adb(*command, timeout=15, check=True):
        return subprocess.run(['adb', '-s', args.serial, *command], check=check,
                              capture_output=True, timeout=timeout)

    if (not args.serial.startswith('emulator-')
            or adb('shell', 'getprop', 'ro.kernel.qemu').stdout.strip() != b'1'
            or adb('shell', 'getprop', 'ro.product.cpu.abi').stdout.strip() != b'x86_64'):
        raise ValueError('Only the owned x86_64 emulator is permitted; never physical hardware')
    if adb('shell', 'getprop', 'sys.boot_completed').stdout.strip() != b'1':
        raise ValueError('Wait for emulator boot')
    artifact = args.artifacts_dir.resolve()
    build = json.loads((artifact / 'build.json').read_text())
    if digest(artifact / 'bridge-tests.apk') != build['apk_sha256']:
        raise ValueError('Build APK hash changed')
    for name, expected in build['source_sha256'].items():
        if digest(ROOT / name) != expected:
            raise ValueError('Rebuild changed source: ' + name)
    path = adb('shell', 'pm', 'path', PACKAGE).stdout.decode().strip()
    if not path.startswith('package:/data/app/') or '\n' in path:
        raise ValueError('Install the reviewed owned fixture first')
    if hashlib.sha256(adb('exec-out', 'cat', path.removeprefix('package:')).stdout).hexdigest() != build['apk_sha256']:
        raise ValueError('Installed APK does not match build')
    session = str(uuid.uuid4())
    evidence = ROOT / '.local/queue-tests/evidence' / session
    evidence.mkdir(parents=True)
    (evidence / 'build.json').write_text(json.dumps(build, indent=2))
    (evidence / 'device.txt').write_bytes(adb('shell', 'getprop', 'ro.build.fingerprint').stdout
                                       + adb('shell', 'getenforce').stdout)
    hosts = set()
    for mode in MODES:
        with (evidence / (mode + '-instrument.txt')).open('wb') as output:
            process = subprocess.Popen(['adb', '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r',
                '-e', 'session', session, '-e', 'mode', mode,
                PACKAGE + '/art.picotronnest.executionlab.QueueInstrumentation'], stdout=output, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 25
                report = None
                while time.monotonic() < deadline:
                    collected = adb('shell', 'run-as', PACKAGE, 'cat',
                                    f'files/queue-{session}/report-{mode}.json', check=False)
                    if collected.returncode == 0:
                        raw = collected.stdout
                        report = json.loads(raw)
                        break
                    if process.poll() is not None:
                        raise ValueError('Instrumentation exited without a report: ' + mode)
                    time.sleep(0.1)
                if report is None:
                    raise ValueError('No fresh queue report: ' + mode)
                (evidence / (mode + '.json')).write_bytes(raw)
                if (report.get('session') != session or report.get('mode') != mode
                        or report.get('official_runtime') != 'NOT TESTED' or report['host_pid'] in hosts):
                    raise ValueError('Stale report, reused host or invalid runtime claim')
                hosts.add(report['host_pid'])
                if mode in BARRIERS:
                    if report.get('outcome') != 'WAITING_FOR_FORCE_STOP' or report.get('detail') != BARRIERS[mode]:
                        raise ValueError('Crash barrier failed: ' + str(report))
                    before = adb('shell', 'ps', '-A', '-n', '-o', 'UID,PID,NAME').stdout.decode()
                    if not any(row.split()[:2] == [str(report['host_uid']), str(report['host_pid'])]
                               for row in before.splitlines()[1:]):
                        raise ValueError('Barrier process already absent')
                    (evidence / (mode + '-before-stop.txt')).write_text(before)
                    adb('shell', 'am', 'force-stop', PACKAGE)
                elif report.get('outcome') != 'PASS' or not report.get('checks'):
                    raise ValueError('Queue checks failed: ' + str(report))
                process.wait(timeout=15)
                census = adb('shell', 'ps', '-A', '-n', '-o', 'UID,PID,NAME').stdout.decode()
                owned = [row for row in census.splitlines()[1:] if row.split() and row.split()[0] == str(report['host_uid'])]
                if any(mode in BARRIERS or int(row.split()[1]) != report['host_pid'] for row in owned):
                    raise ValueError('Surviving process after owned test: ' + str(owned))
                (evidence / (mode + '-census.txt')).write_text('\n'.join(owned))
                print(mode + ': ' + ('STOPPED at ' + BARRIERS[mode] if mode in BARRIERS else 'PASS'), flush=True)
            finally:
                if process.poll() is None:
                    adb('shell', 'am', 'force-stop', PACKAGE)
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=5)
    print('Evidence: ' + str(evidence))


if __name__ == '__main__':
    main()
