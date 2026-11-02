"""Reject private/runtime/build payloads in tracked files and reachable Git history."""
import argparse
import hashlib
import json
import re
from pathlib import Path, PurePosixPath
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DENIED = {'.zip', '.7z', '.tar', '.gz', '.xz', '.deb', '.apk', '.aab', '.apks',
          '.p64', '.p8', '.dat', '.exe', '.dll', '.so', '.dex', '.class',
          '.keystore', '.jks', '.key', '.pem', '.p12', '.pfx'}


def inspect(name, data, proprietary_hashes=()):
    path = PurePosixPath(name.lower())
    if any(part in {'.local', 'dev-runtime', 'node_modules', '.gradle'} for part in path.parts):
        return 'private/generated directory'
    if path.suffix in DENIED or name.lower().endswith(('.p64.png', '.p8.png')):
        return 'runtime/archive/build/signing file'
    if hashlib.sha256(data).hexdigest() in proprietary_hashes:
        return 'known proprietary payload'
    if data.startswith((b'\x7fELF', b'MZ', b'PK\x03\x04', b'7z\xbc\xaf\x27\x1c', b'\x1f\x8b')):
        return 'executable or archive signature'
    if re.search(br'(?m)^-----BEGIN [A-Z ]*PRIVATE KEY-----', data):
        return 'private key'
    if path.suffix == '.png':
        if not name.startswith('docs/screenshots/') or not data.startswith(b'\x89PNG\r\n\x1a\n') or not data.endswith(b'IEND\xaeB`\x82'):
            return 'unreviewed image location/format'
    else:
        try:
            data.decode('utf-8')
        except UnicodeDecodeError:
            return 'unreviewed binary content'
    return None


def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args])


def audit(history=True):
    contract = json.loads((ROOT / 'experiments/execution-model/runtime-contract.json').read_text())
    hashes = {contract['archive_sha256'], *(value['sha256'] for value in contract['files'].values())}
    failures = []
    tracked = git('ls-files', '-z').decode().split('\0')[:-1]
    for name in tracked:
        path = ROOT / name
        reason = 'symlink' if path.is_symlink() else inspect(name, path.read_bytes(), hashes)
        if reason:
            failures.append({'path': name, 'reason': reason, 'scope': 'worktree'})
    seen = set()
    commits = git('rev-list', '--all').decode().splitlines() if history else []
    for commit in commits:
        for entry in git('ls-tree', '-rz', commit).split(b'\0'):
            if not entry:
                continue
            header, rawname = entry.split(b'\t', 1)
            mode, kind, oid = header.decode().split()
            name = rawname.decode('utf-8')
            if (oid, name) in seen:
                continue
            seen.add((oid, name))
            reason = 'non-regular Git entry' if mode not in ('100644', '100755') or kind != 'blob' else inspect(name, git('cat-file', 'blob', oid), hashes)
            if reason:
                failures.append({'path': name, 'object': oid, 'reason': reason, 'scope': 'history'})
    return {'result': 'FAIL' if failures else 'PASS', 'tracked_files': len(tracked),
            'reachable_commits': len(commits), 'historical_entries': len(seen), 'findings': failures}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--current-only', action='store_true')
    args = parser.parse_args()
    result = audit(not args.current_only)
    print(json.dumps(result, indent=2))
    raise SystemExit(bool(result['findings']))
