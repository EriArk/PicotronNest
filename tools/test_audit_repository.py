import unittest
from audit_repository import inspect


class RepositoryAudit(unittest.TestCase):
    def test_hidden_runtime_and_key_payloads(self):
        for name, data in [('file.txt', b'\x7fELFpayload'), ('readme.md', b'PK\x03\x04archive'),
                           ('runtime/PICOTRON.DAT', b'content'), ('cart.p64.png', b'pixels'),
                           ('key.txt', b'-----BEGIN RSA PRIVATE KEY-----'), ('.local/a.txt', b'x')]:
            self.assertIsNotNone(inspect(name, data), name)

    def test_source_and_reviewed_screenshot(self):
        self.assertIsNone(inspect('docs/runtime.md', b'Import picotron.dat privately.'))
        png = b'\x89PNG\r\n\x1a\n' + b'IEND\xaeB`\x82'
        self.assertIsNone(inspect('docs/screenshots/example.png', png))
        self.assertIsNotNone(inspect('unexpected.png', png))


if __name__ == '__main__':
    unittest.main()
