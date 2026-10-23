import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class FrameProtocolHost(unittest.TestCase):
    def test_java_protocol_and_geometry(self):
        here = Path(__file__).resolve().parent
        jdk = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Android\Android Studio\jbr')) / 'bin'
        with tempfile.TemporaryDirectory() as destination:
            subprocess.run([str(jdk / 'javac.exe'), '--release', '8', '-Xlint:all,-options', '-Werror', '-d', destination,
                            str(here / 'src/art/picotronnest/executionlab/FrameProtocol.java'),
                            str(here / 'tests/FrameProtocolHostTest.java')], check=True, capture_output=True, timeout=30)
            subprocess.run([str(jdk / 'java.exe'), '-cp', destination,
                            'art.picotronnest.executionlab.FrameProtocolHostTest'],
                           check=True, capture_output=True, timeout=10)
