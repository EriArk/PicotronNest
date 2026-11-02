import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class AudioHost(unittest.TestCase):
    def test_pcm_transport_bounds(self):
        here = Path(__file__).resolve().parent
        jdk = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Android\Android Studio\jbr')) / 'bin'
        with tempfile.TemporaryDirectory() as temp:
            subprocess.run([str(jdk / 'javac.exe'), '--release', '8', '-Xlint:all,-options', '-Werror', '-d', temp,
                            str(here / 'src/art/picotronnest/executionlab/AudioProtocol.java'),
                            str(here / 'tests/AudioProtocolHostTest.java')], check=True, timeout=30)
            subprocess.run([str(jdk / 'java.exe'), '-cp', temp, 'art.picotronnest.executionlab.AudioProtocolHostTest'], check=True, timeout=10)
