from pathlib import Path
import subprocess
import tempfile
import unittest
from host_tools import default_jdk, executable


class RuntimeStoreHost(unittest.TestCase):
    def test_preservation_activation_and_failures(self):
        here = Path(__file__).resolve().parent
        jdk = default_jdk()
        self.assertIsNotNone(jdk, 'Set JAVA_HOME or put javac on PATH')
        jdk = jdk / 'bin'
        with tempfile.TemporaryDirectory() as temp:
            subprocess.run([str(executable(jdk, 'javac')), '--release', '8', '-encoding', 'UTF-8', '-Xlint:all,-options', '-Werror', '-d', temp,
                            *[str(here / 'src/art/picotronnest/executionlab' / name) for name in ('RuntimeStore.java', 'RuntimeZip.java', 'RuntimeCompatibility.java')],
                            str(here / 'tests/RuntimeStoreChecks.java')], check=True, timeout=30)
            subprocess.run([str(executable(jdk, 'java')), '-cp', temp, 'art.picotronnest.executionlab.RuntimeStoreChecks',
                            str(Path(temp) / 'store-tests')], check=True, timeout=30)
