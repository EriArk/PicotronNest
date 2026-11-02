import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import shutil


class AudioHost(unittest.TestCase):
    def test_pcm_transport_bounds(self):
        here = Path(__file__).resolve().parent
        jdk = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Android\Android Studio\jbr')) / 'bin'
        with tempfile.TemporaryDirectory() as temp:
            subprocess.run([str(jdk / 'javac.exe'), '--release', '8', '-Xlint:all,-options', '-Werror', '-d', temp,
                            str(here / 'src/art/picotronnest/executionlab/AudioProtocol.java'),
                            str(here / 'src/art/picotronnest/executionlab/AudioBufferPolicy.java'),
                            str(here / 'tests/AudioProtocolHostTest.java')], check=True, timeout=30)
            subprocess.run([str(jdk / 'java.exe'), '-cp', temp, 'art.picotronnest.executionlab.AudioProtocolHostTest'], check=True, timeout=10)

    def test_sample_clock(self):
        compiler = shutil.which('gcc')
        if not compiler:
            self.skipTest('GCC is required for the native sample clock check')
        here = Path(__file__).resolve().parent
        with tempfile.TemporaryDirectory() as temp:
            executable = Path(temp) / ('clock.exe' if os.name == 'nt' else 'clock')
            subprocess.run([compiler, '-std=c11', '-Wall', '-Wextra', '-Werror', '-O2',
                            str(here / 'tests/audio_clock_test.c'), '-o', str(executable)], check=True, timeout=30)
            subprocess.run([str(executable)], check=True, timeout=10)
