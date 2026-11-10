import os
import shutil
from pathlib import Path
import subprocess
import tempfile
import unittest


class FolderTransferHost(unittest.TestCase):
    def test_folder_transfers(self):
        here = Path(__file__).resolve().parent
        suffix = '.exe' if os.name == 'nt' else ''
        jdk_home = os.environ.get('JAVA_HOME')
        if not jdk_home and os.name == 'nt':
            jdk_home = r'C:\Program Files\Android\Android Studio\jbr'
        java = str(Path(jdk_home) / 'bin' / ('java' + suffix)) if jdk_home else shutil.which('java')
        javac = str(Path(jdk_home) / 'bin' / ('javac' + suffix)) if jdk_home else shutil.which('javac')
        if not java or not javac:
            self.fail('A JDK is required; set JAVA_HOME or add java and javac to PATH')
        with tempfile.TemporaryDirectory() as temp:
            sources = [here / 'src/art/picotronnest/executionlab' / n for n in ('FolderTransfer.java', 'ProjectFiles.java', 'RuntimeStore.java', 'RuntimeZip.java', 'RuntimeCompatibility.java')]
            sources += [here / 'tests' / n for n in ('FolderTransferChecks.java', 'RuntimeStoreChecks.java')]
            subprocess.run([javac, '--release', '8', '-encoding', 'UTF-8', '-Xlint:all,-options', '-Werror', '-d', temp, *map(str, sources)], check=True, timeout=30)
            subprocess.run([java, '-cp', temp, 'art.picotronnest.executionlab.FolderTransferChecks', str(Path(temp) / 'files')], check=True, timeout=60)
