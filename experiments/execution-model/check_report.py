"""Read-only device evidence collection. The app itself must execute the probes."""

import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess

PACKAGE = 'art.picotronnest.executionlab'
ROOT = Path(__file__).resolve().parents[2]
CASE_NAMES = ['packaged_identity', 'private_direct', 'private_system_linker',
              'packaged_ptrace', 'owned_timeout']


def validate(report, build, census):
    def require(condition, message):
        if not condition:
            raise ValueError(message)

    require(report.get('phase') == 'COMPLETE', 'probe did not complete')
    require(report.get('target_sdk') == 36, 'wrong target SDK')
    require(report.get('picotron_execution') == 'NOT TESTED', 'incorrect Picotron claim')
    matches = [abi for abi, digest in build['fixture_sha256'].items() if digest == report.get('fixture_sha256')]
    require(len(matches) == 1, 'fixture hash does not match build')
    require(matches[0] in report['abis'], 'fixture ABI absent from device ABI list')
    cases = report.get('cases', [])
    require([case.get('name') for case in cases] == CASE_NAMES, 'missing, duplicate or reordered cases')
    children = set()
    for case in cases:
        name = case['name']
        if name == 'private_direct' and case['outcome'] == 'START_FAILED':
            require('error=13' in case.get('error', ''), 'private exec failed for an unexpected reason')
            continue
        require(case.get('outcome') == ('TIMEOUT' if name == 'owned_timeout' else 'EXITED'), 'unexpected outcome')
        require(isinstance(case.get('exit_code'), int), 'missing exit evidence')
        require(case['exit_code'] != 0 if name == 'owned_timeout' else case['exit_code'] == 0, 'unexpected exit code')
        require(case.get('output_truncated') is False, 'truncated output')
        require(name == 'owned_timeout' or 'read_error' not in case, 'unreliable output capture')
        identity = re.search(r'^PICONEST_EXEC_OK pid=(\d+) ppid=(\d+) uid=(\d+)$', case.get('output', ''), re.M)
        require(identity is not None, 'successful callback without native process marker')
        pid, parent, uid = map(int, identity.groups())
        require(parent == report['host_pid'] and uid == report['host_uid'], 'wrong process ownership')
        require(pid > 0 and pid not in children and pid != parent, 'invalid child PID')
        children.add(pid)
        require('selinux=u:r:untrusted_app:' in case['output'], 'probe ran outside app SELinux context')
        require(re.search(r'^seccomp=[012]$', case['output'], re.M), 'missing seccomp observation')
        if name == 'packaged_ptrace':
            require('PICONEST_PTRACE_OK child_exit=37' in case['output'], 'ptrace child was not reaped successfully')
    lines = census.strip().splitlines()
    require(lines and lines[0].split() == ['PID', 'PPID', 'UID', 'NAME'], 'unexpected census header')
    own = []
    for line in lines[1:]:
        fields = line.split(None, 3)
        require(len(fields) == 4 and all(v.isdigit() for v in fields[:3]), 'incomplete process census')
        if int(fields[2]) == report['host_uid']:
            own.append((int(fields[0]), fields[3]))
    require(own == [(report['host_pid'], PACKAGE)], 'probe child remains or host identity changed')
    return matches[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--build', type=Path, default=ROOT / '.local/execution-model/artifacts/build.json')
    args = parser.parse_args()

    def adb(*parts, check=True):
        return subprocess.run(['adb', '-s', args.serial, *parts], capture_output=True, check=check, timeout=20)

    build = json.loads(args.build.read_text(encoding='utf-8'))
    raw = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/execution-probes/latest.json').stdout
    if len(raw) > 65536:
        raise SystemExit('Oversize report')
    report = json.loads(raw)
    census = adb('shell', 'ps', '-A', '-o', 'PID,PPID,UID,NAME').stdout.decode('utf-8')
    abi = validate(report, build, census)
    location = adb('shell', 'pm', 'path', PACKAGE).stdout.decode('utf-8').strip()
    if not re.fullmatch(r'package:/data/app/[A-Za-z0-9_/+=.~-]+/base\.apk', location):
        raise SystemExit('Unexpected package path or split APKs')
    installed = adb('exec-out', 'cat', location.removeprefix('package:')).stdout
    if hashlib.sha256(installed).hexdigest() != build['apk_sha256']:
        raise SystemExit('Installed APK does not match recorded build')
    again = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/execution-probes/latest.json').stdout
    if again != raw:
        raise SystemExit('Session changed during evidence collection')
    enforcing = adb('shell', 'getenforce', check=False)
    evidence = {'collected_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                'build': build, 'report': report, 'verified_abi': abi,
                'own_uid_census': 'host only; no probe children remain',
                'selinux_enforcement_observation': (enforcing.stdout + enforcing.stderr).decode('utf-8').strip(),
                'installed_apk_hash_verified': True}
    folder = ROOT / '.local/execution-model/evidence'
    folder.mkdir(parents=True, exist_ok=True)
    session = report['session']
    if not re.fullmatch(r'[0-9a-f-]{36}', session):
        raise SystemExit('Invalid session identifier')
    destination = folder / (session + '.json')
    destination.write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
    print(f'Evidence verified: {report["model"]}, API {report["api"]}, target 36, {abi}')
    for case in report['cases']:
        print(case['name'], case['outcome'], case.get('exit_code', 'EACCES'))
    print(f'Private report: {destination}')


if __name__ == '__main__':
    main()
