"""Synthetic fixtures only; no purchased runtime required."""

import io
import stat
import struct
import unittest
import zipfile

from inspect_runtime import InspectionError, MAX_MEMBER, inspect_elf, inventory, bounded_read


def make_zip(entries):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w') as archive:
        for name, data in entries:
            archive.writestr(name, data)
    stream.seek(0)
    return stream


class ArchiveChecks(unittest.TestCase):
    def test_normal_archive_and_crc(self):
        with zipfile.ZipFile(make_zip([('picotron/', b''), ('picotron/data', b'owned fixture')])) as z:
            entries = inventory(z)
            self.assertEqual(bounded_read(z, entries[1]), b'owned fixture')

    def test_unsafe_paths(self):
        for name in ('../escape', '/absolute', 'C:/drive', 'a/../b',
                     'a//b', './a', 'a./b', 'a /b', 'a\nname'):
            with self.subTest(name=name), zipfile.ZipFile(make_zip([(name, b'x')])) as z:
                with self.assertRaises(InspectionError):
                    inventory(z)

    def test_backslash_path(self):
        # Windows ZipInfo normalizes backslashes when creating a ZIP. Patch both
        # headers so this fixture actually carries the untrusted backslash.
        raw = make_zip([('dir/file', b'x')]).getvalue().replace(b'dir/file', b'dir\\file')
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            with self.assertRaises(InspectionError):
                inventory(z)

    def test_alias_and_file_directory_collision(self):
        for names in (('Picotron', 'picotron'), ('file', 'file/child'), ('file/child', 'file')):
            with self.subTest(names=names), zipfile.ZipFile(make_zip([(n, b'x') for n in names])) as z:
                with self.assertRaises(InspectionError):
                    inventory(z)

    def test_symlink(self):
        link = zipfile.ZipInfo('link')
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        with zipfile.ZipFile(make_zip([(link, b'../outside')])) as z:
            with self.assertRaises(InspectionError):
                inventory(z)

    def test_declared_oversize(self):
        raw = bytearray(make_zip([('large', b'x')]).getvalue())
        central = raw.index(b'PK\x01\x02')
        struct.pack_into('<I', raw, central + 24, MAX_MEMBER + 1)
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            with self.assertRaises(InspectionError):
                inventory(z)

    def test_corrupt_payload(self):
        raw = make_zip([('file', b'unique payload')]).getvalue().replace(b'unique payload', b'broken payload')
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            with self.assertRaises(zipfile.BadZipFile):
                bounded_read(z, z.getinfo('file'))

    def test_read_bound(self):
        with zipfile.ZipFile(make_zip([('file', b'12345')])) as z:
            with self.assertRaises(InspectionError):
                bounded_read(z, z.getinfo('file'), limit=4)

    def test_truncated_elf_does_not_invoke_tool(self):
        with self.assertRaises(InspectionError):
            inspect_elf(b'\x7fELF', 'nonexistent-readelf')


if __name__ == '__main__':
    unittest.main()
