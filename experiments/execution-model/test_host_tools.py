import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from host_tools import default_sdk, default_jdk, executable, ndk_host
from prepare_desktop import prepare

HERE = Path(__file__).resolve().parent


class HostTools(unittest.TestCase):
    def test_linux_and_windows_resolution(self):
        self.assertEqual(ndk_host('linux', 'x86_64'), 'linux-x86_64')
        self.assertEqual(ndk_host('win32', 'AMD64'), 'windows-x86_64')
        self.assertEqual(executable(Path('sdk'), 'clang', 'linux').name, 'clang')
        self.assertEqual(executable(Path('sdk'), 'clang', 'win32').name, 'clang.exe')
        for host, machine in [('darwin', 'arm64'), ('linux', 'aarch64')]:
            with self.assertRaises(ValueError):
                ndk_host(host, machine)

    def test_explicit_environment_and_help_without_windows_environment(self):
        with patch.dict(os.environ, {'ANDROID_HOME': '/owned/sdk', 'JAVA_HOME': '/owned/jdk'}, clear=True):
            self.assertEqual(default_sdk(), Path('/owned/sdk'))
            self.assertEqual(default_jdk(), Path('/owned/jdk'))
        environment = dict(os.environ)
        environment.pop('LOCALAPPDATA', None)
        result = subprocess.run([sys.executable, '-B', str(HERE / 'build.py'), '--help'],
                                env=environment, capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('--artifacts-dir', result.stdout)

    def test_stale_base_rejected_before_extraction(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            apk = root / 'base.apk'
            apk.write_bytes(b'owned invalid base; do not extract')
            (root / 'build.json').write_text(json.dumps({
                'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                'source_sha256': {},
            }))
            with self.assertRaisesRegex(ValueError, 'rebuild --headless'):
                prepare(root / 'output', root / 'llvm', apk)
            self.assertFalse((root / 'output').exists())

    def test_explicit_sdk_does_not_silently_fall_back(self):
        with tempfile.TemporaryDirectory() as folder:
            result = subprocess.run([sys.executable, '-B', str(HERE / 'build.py'), '--sdk', folder, '--jdk', folder],
                                    capture_output=True, text=True, timeout=10)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('Required build input missing', result.stderr)
            self.assertIn(str(Path(folder).resolve()), result.stderr)
