"""Read-only device evidence collection. The app itself must execute the probes."""

import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess
import struct

PACKAGE = 'art.picotronnest.executionlab'
ROOT = Path(__file__).resolve().parents[2]
CASE_NAMES = ['packaged_identity', 'private_direct', 'private_system_linker',
              'packaged_ptrace', 'owned_timeout']


def validate_frame(raw):
    if len(raw) < 16:
        raise ValueError('Short frame header')
    magic, width, height, sequence = struct.unpack_from('<4I', raw)
    if magic != 0x504e4631 or not (0 < width <= 2048 and 0 < height <= 2048) or sequence == 0:
        raise ValueError('Invalid frame header')
    if len(raw) != 16 + width * height * 4:
        raise ValueError('Frame payload length mismatch')
    return {'width': width, 'height': height, 'stride': width * 4, 'format': 'RGBA32',
            'sequence': sequence, 'sha256': hashlib.sha256(raw).hexdigest()}


def validate(report, build, census):
    def require(condition, message):
        if not condition:
            raise ValueError(message)

    require(report.get('phase') == 'COMPLETE', 'probe did not complete')
    require(report.get('target_sdk') == 36, 'wrong target SDK')
    headless = 'headless_components' in build
    require(report.get('picotron_execution') == ('ATTEMPTED' if headless else 'NOT TESTED'), 'incorrect Picotron claim')
    matches = [abi for abi, digest in build['fixture_sha256'].items() if digest == report.get('fixture_sha256')]
    require(len(matches) == 1, 'fixture hash does not match build')
    require(matches[0] in report['abis'], 'fixture ABI absent from device ABI list')
    cases = report.get('cases', [])
    expected_names = list(CASE_NAMES)
    if 'linux_components' in build:
        require(report.get('schema') == (4 if build.get('desktop_probe') else (3 if headless else 2)), 'Linux report schema missing')
        components = build['linux_components']
        require(report.get('linux_components') == components['packages'], 'native component manifest mismatch')
        require(report.get('linux_guest_sha256') == components['guests'][matches[0]], 'guest hash mismatch')
        expected_names += ['linux_guest', 'linux_guest_missing_marker']
    if headless:
        require(matches[0] == 'arm64-v8a', 'headless ABI mismatch')
        require(report.get('headless_components') == build['headless_components'], 'headless closure mismatch')
        require(report.get('runtime_contract') == build['runtime_contract'], 'owner runtime contract mismatch')
        expected_names += ['picotron_headless_1', 'picotron_headless_2']
    if build.get('desktop_probe'):
        expected_names += ['picotron_desktop_trace']
    require([case.get('name') for case in cases] == expected_names, 'missing, duplicate or reordered cases')
    children = set()
    for case in cases:
        name = case['name']
        if name == 'picotron_desktop_trace':
            require(case.get('outcome') == 'EXITED' and case.get('exit_code') == 0, 'desktop termination failed')
            require(case.get('output_truncated') is False and 'read_error' not in case, 'desktop output unreliable')
            output = case.get('output', '')
            identity = re.search(r'^PICONEST_TRACE_LOADED pid=(\d+) uid=(\d+)$', output, re.M)
            require(identity is not None, 'desktop process observer not loaded')
            pid, uid = map(int, identity.groups())
            require(uid == report['host_uid'] and pid > 0 and pid not in children and pid != report['host_pid'],
                    'desktop process identity invalid')
            children.add(pid)
            require('PICONEST_KEYBOARD_ERROR' not in output, 'native keyboard channel failed')
            if 'PICONEST_KEYBOARD_' in output or 'desktop_keyboard' in report:
                require(build.get('desktop_keyboard_bridge') is True, 'keyboard bridge absent from build')
                host = report.get('desktop_keyboard')
                require(isinstance(host, dict) and 'error' not in host and host.get('peer_pid') == pid,
                        'keyboard host peer evidence missing or invalid')
                records = re.findall(r'^PICONEST_KEYBOARD_DONE packets=(\d+) downs=(\d+) ups=(\d+) repeats=(\d+) '
                                     r'held=(\d+) events=(\d+) text_events=(\d+) text_bytes=(\d+) suppressed=(\d+) '
                                     r'pending=(\d+) buffered=(\d+) failed=(\d+)$', output, re.M)
                require(len(records) == 1 and output.count('PICONEST_KEYBOARD_CONNECTED') == 1,
                        'keyboard connection/termination evidence missing or duplicated')
                packets, downs, ups, repeats, held, events, texts, text_bytes, suppressed, pending, buffered, failed = map(int, records[0])
                require(type(host.get('packets')) is int and type(host.get('accepted')) is int
                        and host['packets'] == host['accepted'] == packets, 'keyboard packets lost or unconfirmed')
                require(downs == ups and held == pending == buffered == failed == suppressed == 0,
                        'keyboard state, pending input or text delivery unconfirmed')
                require(events == downs + ups + repeats + texts and texts <= text_bytes <= 31 * texts,
                        'keyboard SDL event accounting mismatch')
            if build.get('desktop_live'):
                live = report.get('desktop_frames', {})
                require('error' not in live and live.get('frames', 0) >= 10, 'live frame receiver failed')
                require(live.get('peer_pid') == pid, 'frame socket peer is not the observed Picotron process')
                require(0 < live.get('drawn_sequence', 0) <= live.get('sequence', 0), 'no Android frame draw evidence')
                require(0 < live.get('width', 0) <= 2048 and 0 < live.get('height', 0) <= 2048, 'invalid live dimensions')
                require('PICONEST_FRAME_SEND_FAILED' not in output, 'frame sender failed')
                if build.get('desktop_pointer'):
                    require('pointer_error' not in live, 'pointer sender failed')
                    counters = re.search(r'PICONEST_INPUT_DONE packets=(\d+) downs=(\d+) ups=(\d+) held=(\d+)', output)
                    require(counters is not None, 'pointer termination evidence missing')
                    packets, downs, ups, held = map(int, counters.groups())
                    require(packets == live.get('pointer_packets') and downs == ups and held == 0,
                            'pointer updates lost or button left held')
                    require('PICONEST_INPUT_INVALID' not in output, 'native pointer protocol rejected')
            require('PICONEST_PRESENT ' in output and 'PICONEST_FRAME_CAPTURE ' in output, 'no actual captured presentation')
            require('PICONEST_TRACE_QUIT' in output and 'PICONEST_TRACE_DONE' in output, 'desktop shutdown not observed')
            continue
        if name.startswith('picotron_headless_'):
            require(case.get('outcome') == 'EXITED' and case.get('exit_code') == 0, 'Picotron termination failed')
            require(case.get('output_truncated') is False and 'read_error' not in case, 'Picotron output unreliable')
            output = case.get('output', '')
            identity = re.search(r'^PICONEST_EXEC_PICOTRON pid=(\d+) ppid=(\d+) uid=(\d+)$', output, re.M)
            require(identity is not None, 'Picotron exec identity missing')
            pid, parent, uid = map(int, identity.groups())
            require(uid == report['host_uid'] and parent > 0 and pid > 0 and pid != parent
                    and pid not in children and pid != report['host_pid'], 'Picotron identity invalid')
            children.add(pid)
            marker = report['session'] + ':' + name.rsplit('_', 1)[1]
            require('PICONEST_GLIBC glibc=2.36 sdl=2.32.10\n' in output, 'glibc/SDL fixture not observed')
            require('PICONEST_EXEC_FAILED' not in output, 'Picotron exec failed')
            for tag in ('LUA', 'OK'):
                require('PICONEST_PICOTRON_' + tag + ' ' + marker + '\n' in output,
                        'official Lua did not produce the current attempt marker')
            continue
        if name.startswith('linux_guest'):
            missing = name == 'linux_guest_missing_marker'
            require(case.get('outcome') == 'EXITED' and case.get('exit_code') == (41 if missing else 0),
                    'guest termination failed')
            require(case.get('output_truncated') is False and 'read_error' not in case, 'guest output unreliable')
            output = case.get('output', '')
            identity = re.search(r'^PICONEST_LINUX_GUEST pid=(\d+) ppid=(\d+) uid=(\d+)$', output, re.M)
            require(identity is not None, 'guest identity missing')
            pid, parent, uid = map(int, identity.groups())
            require(uid == report['host_uid'] and parent > 0 and pid > 0 and pid != parent
                    and pid not in children and pid != report['host_pid'], 'guest identity invalid')
            children.add(pid)
            if missing:
                require('PICONEST_MARKER_MISSING' in output and 'PICONEST_LINUX_GUEST_OK' not in output,
                        'missing marker incorrectly passed')
            else:
                require('PICONEST_GUEST_MARKER ' + report['session'] + '\n' in output
                        and 'PICONEST_LINUX_GUEST_OK' in output, 'wrong guest root or stale session marker')
            continue
        if name == 'private_direct' and case['outcome'] == 'START_FAILED':
            require('error=13' in case.get('error', ''), 'private exec failed for an unexpected reason')
            continue
        require(case.get('outcome') == ('TIMEOUT' if name == 'owned_timeout' else 'EXITED'), 'unexpected outcome')
        require(isinstance(case.get('exit_code'), int), 'missing exit evidence')
        require(case['exit_code'] != 0 if name == 'owned_timeout' else case['exit_code'] == 0, 'unexpected exit code')
        require(case.get('output_truncated') is False, 'truncated output')
        if name == 'owned_timeout':
            require(case.get('ready_observed') is True and 'PICONEST_HANG_READY' in case.get('output', ''),
                    'timeout did not observe a running hang fixture')
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
    journal = 'desktop-probes' if build.get('desktop_probe') else ('headless-probes' if 'headless_components' in build else 'execution-probes')
    report_path = 'files/' + journal + '/latest.json'
    raw = adb('exec-out', 'run-as', PACKAGE, 'cat', report_path).stdout
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
    again = adb('exec-out', 'run-as', PACKAGE, 'cat', report_path).stdout
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
    if build.get('desktop_probe'):
        frame = adb('exec-out', 'run-as', PACKAGE, 'cat',
                    'files/desktop-probes/' + session + '/headless-root/probe/frame.rgba').stdout
        evidence['captured_frame'] = validate_frame(frame)
        (folder / (session + '.rgba')).write_bytes(frame)
    destination = folder / (session + '.json')
    destination.write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
    print(f'Evidence verified: {report["model"]}, API {report["api"]}, target 36, {abi}')
    for case in report['cases']:
        print(case['name'], case['outcome'], case.get('exit_code', 'EACCES'))
    print(f'Private report: {destination}')


if __name__ == '__main__':
    main()
