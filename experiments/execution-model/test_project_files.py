import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class ProjectFilesHost(unittest.TestCase):
    def test_preserving_transfers(self):
        here = Path(__file__).resolve().parent
        jdk = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Android\Android Studio\jbr')) / 'bin'
        with tempfile.TemporaryDirectory() as temp:
            sources = [here / 'src/art/picotronnest/executionlab' / n for n in ('ProjectFiles.java', 'RuntimeStore.java', 'RuntimeZip.java')]
            sources += [here / 'tests' / n for n in ('ProjectFilesChecks.java', 'RuntimeStoreChecks.java')]
            subprocess.run([str(jdk / 'javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-Xlint:all,-options', '-Werror', '-d', temp, *map(str, sources)], check=True, timeout=30)
            subprocess.run([str(jdk / 'java.exe'), '-cp', temp, 'art.picotronnest.executionlab.ProjectFilesChecks', str(Path(temp) / 'files')], check=True, timeout=20)
