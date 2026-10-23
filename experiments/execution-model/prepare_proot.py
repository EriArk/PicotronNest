"""Prepare the pinned, audited native closure; never unpack archive paths."""
import hashlib
import io
import json
from pathlib import Path
import struct
import tarfile
import urllib.request
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
CACHE = ROOT / '.local/proot/downloads'


def fetch(item):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / item['file']
    if path.exists():
        data = path.read_bytes()
    else:
        with urllib.request.urlopen(item['url'], timeout=30) as response:
            data = response.read(16 * 1024 * 1024 + 1)
    if len(data) > 16 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != item['sha256']:
        raise ValueError(f'Checksum/size mismatch: {item["file"]}')
    path.write_bytes(data)
    return data


def deb_payload(data):
    if data[:8] != b'!<arch>\n':
        raise ValueError('Not a Debian ar archive')
    offset, found = 8, []
    while offset < len(data):
        header = data[offset:offset + 60]
        if len(header) != 60 or header[58:] != b'`\n':
            raise ValueError('Invalid ar header')
        size = int(header[48:58])
        name = header[:16].decode('ascii').strip().rstrip('/')
        start, end = offset + 60, offset + 60 + size
        if size < 0 or end > len(data):
            raise ValueError('Invalid ar member size')
        if name in ('data.tar.xz', 'data.tar.gz'):
            found.append(data[start:end])
        offset = end + size % 2
    if len(found) != 1:
        raise ValueError('Expected one supported Debian payload')
    return found[0]


def tar_member(data, name):
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:*') as archive:
        matches = [m for m in archive.getmembers() if m.name == name]
        if len(matches) != 1 or not matches[0].isfile() or matches[0].size > 1024 * 1024:
            raise ValueError('Missing, duplicate or invalid allowlisted member')
        with archive.extractfile(matches[0]) as stream:
            return stream.read()


def adapt_dynamic_strings(data):
    """Change only bounded dynamic-string values, preserving ELF layout/code."""
    if data[:7] != b'\x7fELF\x02\x01\x01':
        raise ValueError('Expected little-endian ELF64')
    header = struct.unpack_from('<HHIQQQIHHHHHH', data, 16)
    segments = [struct.unpack_from('<IIQQQQQQ', data, header[4] + n * header[8])
                for n in range(header[9])]
    dynamic = []
    for segment in segments:
        if segment[0] != 2:
            continue
        for offset in range(segment[2], segment[2] + segment[5], 16):
            tag, value = struct.unpack_from('<qQ', data, offset)
            if tag == 0:
                break
            dynamic.append((tag, value))
    if not dynamic:  # Standalone static loader.
        return data, []
    address = next(value for tag, value in dynamic if tag == 5)
    strsize = next(value for tag, value in dynamic if tag == 10)
    segments = [s for s in segments if s[0] == 1 and s[3] <= address < s[3] + s[5]]
    if len(segments) != 1:
        raise ValueError('Invalid dynamic string table')
    start = segments[0][2] + address - segments[0][3]
    result, changes = bytearray(data), []
    replacements = {b'libtalloc.so.2': b'libtalloc.so',
                    b'/data/data/com.termux/files/usr/lib': b'$ORIGIN'}
    for tag, value in dynamic:
        if tag not in (1, 14, 15, 29):
            continue
        if value >= strsize or start + strsize > len(data):
            raise ValueError('Dynamic string exceeds table')
        offset = start + value
        end = data.index(b'\0', offset, start + strsize)
        original = data[offset:end]
        if original in replacements:
            updated = replacements[original]
            if len(updated) > len(original):
                raise ValueError('Replacement cannot grow dynamic string')
            result[offset:end] = updated.ljust(len(original), b'\0')
            changes.append({'tag': tag, 'from': original.decode(), 'to': updated.decode()})
    return bytes(result), changes


def prepare(destination):
    lock = json.loads((HERE / 'proot.lock.json').read_text(encoding='utf-8'))
    files, manifest = {}, {'packages': {}, 'sources': lock['sources'], 'recipe_commit': lock['recipe_commit']}
    # Fetch exact corresponding upstream sources and license notices before packaging binaries.
    for source in lock['sources']:
        raw = fetch(source)
        for name, entry in source.get('notices', {}).items():
            if source['file'].endswith('.zip'):
                with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                    content = archive.read(entry)
            elif entry is None:
                content = raw
            else:
                content = tar_member(raw, entry)
            if name == 'TALLOC-COPYRIGHT.txt':
                content = content[:content.index(b'*/') + 2] + b'\n'
            target = destination / 'assets/licenses' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            files['assets/licenses/' + name] = target
    for recipe in lock['recipes']:
        fetch(recipe)
    for package in lock['packages']:
        payload = deb_payload(fetch(package))
        for member in package['members']:
            original = tar_member(payload, member['path'])
            if hashlib.sha256(original).hexdigest() != member['sha256']:
                raise ValueError('Upstream ELF member hash mismatch')
            output, changes = adapt_dynamic_strings(original)
            apk_path = f'lib/{package["abi"]}/{member["output"]}'
            target = destination / apk_path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(output)
            files[apk_path] = target
            manifest['packages'][apk_path] = {'sha256': hashlib.sha256(output).hexdigest(),
                                            'upstream_sha256': member['sha256'], 'changes': changes}
    source_map = destination / 'assets/licenses/source-manifest.json'
    source_map.write_text(json.dumps(lock, indent=2) + '\n', encoding='utf-8')
    files['assets/licenses/source-manifest.json'] = source_map
    return files, manifest
