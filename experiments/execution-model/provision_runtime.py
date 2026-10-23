"""Privately provision the exact owner runtime into the existing debug lab, never an APK."""
import argparse
import base64
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import shlex
import zipfile

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[1] / 'tools'))
from inspect_runtime import inventory, bounded_read, MAX_ARCHIVE

PACKAGE = 'art.picotronnest.executionlab'


def read_owner(path, contract):
    if path.stat().st_size > MAX_ARCHIVE:
        raise ValueError('Oversize owner archive')
    with zipfile.ZipFile(path) as outer:
        inventory(outer)
        data = bounded_read(outer, outer.getinfo(contract['member']))
    if hashlib.sha256(data).hexdigest() != contract['archive_sha256']:
        raise ValueError('This bounded probe only supports the inspected owner package')
    result = {}
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        inventory(archive)
        for name, expected in contract['files'].items():
            data = bounded_read(archive, archive.getinfo('picotron/' + name), 16 * 1024 * 1024)
            if len(data) != expected['size'] or hashlib.sha256(data).hexdigest() != expected['sha256']:
                raise ValueError('Owner member hash/size mismatch')
            result[name] = data
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--archive', type=Path, required=True)
    args = parser.parse_args()
    contract = json.loads((HERE / 'runtime-contract.json').read_text(encoding='utf-8'))
    files = read_owner(args.archive, contract)

    def adb(*parts, data=None, check=True):
        return subprocess.run(['adb', '-s', args.serial, *parts], input=data,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check, timeout=30)

    build = json.loads((HERE.parents[1] / '.local/execution-model/artifacts/build.json').read_text(encoding='utf-8'))
    if build.get('runtime_contract') != contract:
        raise ValueError('Build the headless lab first')
    location = adb('shell', 'pm', 'path', PACKAGE).stdout.decode().strip()
    import re
    if not re.fullmatch(r'package:/data/app/[A-Za-z0-9_/+=.~-]+/base\.apk', location):
        raise ValueError('Unexpected lab installation')
    installed = adb('exec-out', 'cat', location.removeprefix('package:')).stdout
    if hashlib.sha256(installed).hexdigest() != build['apk_sha256']:
        raise ValueError('Install the current headless lab before provisioning')
    target = 'files/headless-import/' + contract['archive_sha256']
    # Fail closed on an existing candidate. Never overwrite an unknown/incomplete import.
    exists = adb('shell', 'run-as', PACKAGE, 'test', '-e', target, check=False)
    if exists.returncode == 0:
        raise ValueError('Import already exists; inspect it before any retry')
    adb('shell', 'run-as', PACKAGE, 'mkdir', '-p', target)
    for name, data in files.items():
        # Encode binary stdin: Windows adb stdin truncated binary data in this probe.
        # All shell syntax is fixed and the destination comes from the reviewed contract.
        decode = 'base64 -d > ' + shlex.quote(target + '/' + name)
        command = shlex.join(['run-as', PACKAGE, 'sh', '-c', decode])
        adb('shell', '-T', command, data=base64.encodebytes(data))
        actual = adb('exec-out', 'run-as', PACKAGE, 'sha256sum', target + '/' + name).stdout.decode().split()[0]
        if actual != contract['files'][name]['sha256']:
            raise ValueError('Device copy hash mismatch; incomplete import retained')
        adb('shell', 'run-as', PACKAGE, 'chmod', '400', target + '/' + name)
    adb('shell', '-T', 'run-as', PACKAGE, 'dd', 'of=' + target + '/ready', data=b'validated\n')
    print('Exact owner runtime privately provisioned; both device hashes verified. No runtime process was launched.')


if __name__ == '__main__':
    main()
