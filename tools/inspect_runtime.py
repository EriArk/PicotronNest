"""Private, static ZIP/ELF inspection. Never executes the imported runtime."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import stat
import struct
import subprocess
import tempfile
import zipfile


# Research limits, not a promised product import contract.
MAX_ARCHIVE = 256 * 1024 * 1024
MAX_TOTAL = 512 * 1024 * 1024
MAX_MEMBER = 128 * 1024 * 1024
MAX_ENTRIES = 4096
MAX_ELF = 32 * 1024 * 1024


class InspectionError(ValueError):
    pass


def inventory(archive):
    """Validate the complete central directory before reading any payload."""
    entries = archive.infolist()
    if len(entries) > MAX_ENTRIES:
        raise InspectionError("too many archive entries")
    seen = {}
    total = 0
    for item in entries:
        name = item.orig_filename
        parts = name.rstrip('/').split('/')
        if (not name or '\\' in name or ':' in name
                or any(ord(c) < 32 or ord(c) == 127 for c in name)
                or any(p in ('', '.', '..') or p.endswith((' ', '.')) for p in parts)):
            raise InspectionError("unsafe or ambiguous archive path")
        key = '/'.join(parts).casefold()
        if key in seen:
            raise InspectionError("duplicate or case-aliased archive path")
        kind = stat.S_IFMT(item.external_attr >> 16)
        if kind not in (0, stat.S_IFREG, stat.S_IFDIR):
            raise InspectionError("symlink or special archive entry")
        if kind == stat.S_IFDIR and not item.is_dir():
            raise InspectionError("inconsistent directory entry")
        if item.flag_bits & 1:
            raise InspectionError("encrypted archive entry")
        if item.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
            raise InspectionError("unsupported compression method")
        if item.file_size > MAX_MEMBER:
            raise InspectionError("archive member exceeds inspection limit")
        if item.is_dir() and item.file_size:
            raise InspectionError("directory has a payload")
        total += item.file_size
        seen[key] = item.is_dir()
    if total > MAX_TOTAL:
        raise InspectionError("archive exceeds expanded-size limit")
    for key in seen:
        parts = key.split('/')
        if any(seen.get('/'.join(parts[:n])) is False for n in range(1, len(parts))):
            raise InspectionError("file/directory collision")
    return entries


def bounded_read(archive, item, limit=MAX_MEMBER):
    if item.file_size > limit:
        raise InspectionError("selected member exceeds inspection limit")
    with archive.open(item) as stream:
        data = stream.read(limit + 1)
    if len(data) > limit or len(data) != item.file_size:
        raise InspectionError("member size mismatch")
    return data


def inspect_elf(data, readelf):
    if len(data) < 64 or data[:4] != b'\x7fELF':
        raise InspectionError("truncated or invalid ELF header")
    if data[4:7] != b'\x02\x01\x01':
        raise InspectionError("probe requires ELF64 little-endian version 1")
    machine = struct.unpack_from('<H', data, 18)[0]
    # Fixed temporary filename outside the repository; no archive path extraction.
    with tempfile.TemporaryDirectory(prefix='picotronnest-inspect-') as folder:
        binary = Path(folder) / 'candidate.elf'
        binary.write_bytes(data)
        env = dict(os.environ, LC_ALL='C', LANG='C')
        result = subprocess.run(
            [readelf, '--wide', '-h', '-l', '-d', '-V', '--dyn-syms', str(binary)],
            capture_output=True, text=True, encoding='utf-8', errors='replace',
            timeout=30, env=env, check=False,
        )
    if result.returncode or result.stderr.strip():
        raise InspectionError("readelf rejected the candidate; inspect privately")
    output = result.stdout
    interpreter = re.search(r'Requesting program interpreter: ([^\]]+)', output)
    return {
        'sha256': hashlib.sha256(data).hexdigest(),
        'class': 'ELF64', 'endianness': 'little', 'machine_id': machine,
        'architecture': 'AArch64' if machine == 183 else f'ELF machine {machine}',
        'arm64_candidate': machine == 183,
        'interpreter': interpreter.group(1) if interpreter else None,
        'needed': re.findall(r'\(NEEDED\).*?\[([^\]]+)\]', output),
        'search_paths': re.findall(r'\((?:RPATH|RUNPATH)\).*?\[([^\]]+)\]', output),
        'glibc_versions': sorted(set(re.findall(r'GLIBC_[0-9.]+', output))),
        'sdl_imports': sorted(set(re.findall(r'\bUND\s+(SDL_\w+)', output))),
        'render_path': 'NOT OBSERVED: imports are not a call trace',
    }


def inspect_package(archive, readelf):
    entries = inventory(archive)
    report = {'entries': [], 'elf_files': [], 'manual_version_markers': []}
    for item in entries:
        report['entries'].append({'path': item.filename, 'size': item.file_size})
        if item.is_dir():
            continue
        # Read each selected-package member to EOF, checking CRC and declared size.
        data = bounded_read(archive, item)
        if data.startswith(b'\x7fELF'):
            if len(data) > MAX_ELF:
                raise InspectionError("ELF exceeds inspection limit")
            report['elf_files'].append({'path': item.filename, **inspect_elf(data, readelf)})
        if item.filename.lower().endswith('manual.txt'):
            markers = re.findall(rb'(?m)^Picotron v[0-9A-Za-z.]+\r?$', data)
            report['manual_version_markers'].extend(m.decode('ascii').strip() for m in markers)
    if not report['elf_files']:
        raise InspectionError("no ELF executable in selected package")
    report['arm64_candidate'] = any(e['arm64_candidate'] for e in report['elf_files'])
    report['execution'] = 'NOT TESTED'
    return report


def inspect_archive(path, member, readelf):
    with Path(path).open('rb') as stream:
        if os.fstat(stream.fileno()).st_size > MAX_ARCHIVE:
            raise InspectionError("input archive exceeds inspection limit")
        outer_hash = hashlib.file_digest(stream, 'sha256').hexdigest()
        stream.seek(0)
        with zipfile.ZipFile(stream) as outer:
            inventory(outer)
            if member:
                try:
                    item = outer.getinfo(member)
                except KeyError as error:
                    raise InspectionError("selected member not found") from error
                raw = bounded_read(outer, item)
                with zipfile.ZipFile(io.BytesIO(raw)) as inner:
                    report = inspect_package(inner, readelf)
                package_hash = hashlib.sha256(raw).hexdigest()
            else:
                report = inspect_package(outer, readelf)
                package_hash = outer_hash
    return {'schema': 1, 'outer_sha256': outer_hash, 'selected_member': member,
            'package_sha256': package_hash, **report}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--member', help='Exact nested ZIP member; no automatic recursion')
    parser.add_argument('--readelf', default=shutil.which('readelf'), help='GNU readelf executable')
    args = parser.parse_args()
    if not args.readelf:
        parser.error('GNU readelf required; pass --readelf PATH')
    try:
        report = inspect_archive(args.archive, args.member, args.readelf)
        version = subprocess.run([args.readelf, '--version'], capture_output=True,
                                 text=True, timeout=10, check=True).stdout.splitlines()[0]
        report['inspector'] = version
    except (InspectionError, OSError, zipfile.BadZipFile, RuntimeError,
            subprocess.SubprocessError) as error:
        # Do not echo local filesystem paths or binary/tool output into shared logs.
        message = str(error) if isinstance(error, InspectionError) else type(error).__name__
        parser.exit(2, f'Inspection failed: {message}\n')
    print(json.dumps(report, indent=2, ensure_ascii=True))
    return 0 if report['arm64_candidate'] else 3


if __name__ == '__main__':
    raise SystemExit(main())
