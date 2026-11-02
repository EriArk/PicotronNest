import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class RuntimeStoreHost(unittest.TestCase):
    def test_preservation_activation_and_failures(self):
        here = Path(__file__).resolve().parent
        jdk = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Android\Android Studio\jbr')) / 'bin'
        with tempfile.TemporaryDirectory() as temp:
            subprocess.run([str(jdk / 'javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-Xlint:all,-options', '-Werror', '-d', temp,
                            *[str(here / 'src/art/picotronnest/executionlab' / name) for name in ('RuntimeStore.java', 'RuntimeZip.java')],
                            str(here / 'tests/RuntimeStoreChecks.java')], check=True, timeout=30)
            subprocess.run([str(jdk / 'java.exe'), '-cp', temp, 'art.picotronnest.executionlab.RuntimeStoreChecks',
                            str(Path(temp) / 'store-tests')], check=True, timeout=30)
