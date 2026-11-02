import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class GameLibraryHost(unittest.TestCase):
    def test_native_library_contract(self):
        here = Path(__file__).resolve().parent
        jdk = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Android\Android Studio\jbr')) / 'bin'
        with tempfile.TemporaryDirectory() as temp:
            sources = [here / 'src/art/picotronnest/executionlab' / n for n in
                       ('GameLibrary.java', 'ProjectFiles.java', 'RuntimeStore.java', 'RuntimeZip.java', 'RuntimeCompatibility.java')]
            subprocess.run([str(jdk / 'javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-Xlint:all,-options', '-Werror',
                            '-d', temp, *map(str, sources), str(here / 'tests/GameLibraryChecks.java')], check=True, timeout=30)
            subprocess.run([str(jdk / 'java.exe'), '-cp', temp, 'art.picotronnest.executionlab.GameLibraryChecks',
                            str(Path(temp) / 'library')], check=True, timeout=30)
