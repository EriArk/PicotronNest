import hashlib
import io
from pathlib import Path
import struct
import tarfile
import tempfile
import unittest
from unittest.mock import patch

from prepare_proot import adapt_dynamic_strings, deb_payload, fetch, tar_member


def elf_fixture():
    data = bytearray(1024)
    data[:7] = b'\x7fELF\x02\x01\x01'
    struct.pack_into('<HHIQQQIHHHHHH', data, 16, 3, 183, 1, 0, 64, 0, 0, 64, 56, 2, 0, 0, 0)
    struct.pack_into('<IIQQQQQQ', data, 64, 1, 4, 0, 4096, 4096, 1024, 1024, 4096)
    struct.pack_into('<IIQQQQQQ', data, 120, 2, 4, 256, 4352, 4352, 80, 80, 8)
    strings = b'libtalloc.so.2\0/data/data/com.termux/files/usr/lib\0'
    for index, pair in enumerate([(5, 4608), (10, len(strings)), (1, 0), (29, 15), (0, 0)]):
        struct.pack_into('<qQ', data, 256 + index * 16, *pair)
    data[512:512 + len(strings)] = strings
    data[800:815] = b'libtalloc.so.2\0'  # Outside the dynamic table, must not change.
    return bytes(data), len(strings)


class PreparationChecks(unittest.TestCase):
    def test_patch_only_changes_dynamic_strings(self):
        data, length = elf_fixture()
        result, changes = adapt_dynamic_strings(data)
        self.assertEqual(len(changes), 2)
        self.assertEqual(result[:512], data[:512])
        self.assertEqual(result[512 + length:], data[512 + length:])
        self.assertEqual(len(result), len(data))
        self.assertTrue(result[512:].startswith(b'libtalloc.so\0\0\0$ORIGIN\0'))

    def test_patch_is_idempotent(self):
        data, _ = elf_fixture()
        once, _ = adapt_dynamic_strings(data)
        twice, changes = adapt_dynamic_strings(once)
        self.assertEqual(once, twice)
        self.assertEqual(changes, [])

    def test_non_elf_rejected(self):
        with self.assertRaises(ValueError):
            adapt_dynamic_strings(b'not ELF')

    def test_bad_ar_header_rejected(self):
        for raw in (b'not ar', b'!<arch>\ntruncated'):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                deb_payload(raw)

    def test_tar_symlink_is_not_payload(self):
        buffer = io.BytesIO()
        with tarfile.open(fileobj=buffer, mode='w') as archive:
            member = tarfile.TarInfo('selected')
            member.type = tarfile.SYMTYPE
            member.linkname = '/outside'
            archive.addfile(member)
        with self.assertRaises(ValueError):
            tar_member(buffer.getvalue(), 'selected')

    def test_modified_cache_is_rejected_without_fetch(self):
        with tempfile.TemporaryDirectory() as folder, patch('prepare_proot.CACHE', Path(folder)):
            Path(folder, 'input').write_bytes(b'modified')
            with patch('urllib.request.urlopen') as network, self.assertRaises(ValueError):
                fetch({'file': 'input', 'url': 'https://unused.invalid', 'sha256': hashlib.sha256(b'expected').hexdigest()})
            network.assert_not_called()


if __name__ == '__main__':
    unittest.main()
