import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class FrameProtocolHost(unittest.TestCase):
    def test_ordered_input_acknowledgements_and_backpressure(self):
        self.run_java('OrderedInputHostTest')

    def test_java_protocol_and_geometry(self):
        self.run_java('FrameProtocolHostTest')

    def test_transport_and_failure_boundaries(self):
        self.run_java('TransportHostTest')

    def test_secondary_button_and_wheel_transport(self):
        self.run_java('PointerProtocolHostTest')

    def test_keyboard_state_unicode_and_transport(self):
        self.run_java('KeyboardHostTest')

    def test_relative_cursor_ownership_and_timing(self):
        self.run_java('RelativePointerHostTest')

    def test_dpad_state_and_source_ownership(self):
        self.run_java('DpadHostTest')

    def test_cursor_profile_schema_and_rejection(self):
        self.run_java('CursorProfileHostTest')

    def test_session_exit_waits_for_verified_backend_once(self):
        self.run_java('SessionExitHostTest')

    def run_java(self, test):
        here = Path(__file__).resolve().parent
        jdk = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Android\Android Studio\jbr')) / 'bin'
        with tempfile.TemporaryDirectory() as destination:
            subprocess.run([str(jdk / 'javac.exe'), '--release', '8', '-Xlint:all,-options', '-Werror', '-d', destination,
                            str(here / 'src/art/picotronnest/executionlab/FrameProtocol.java'),
                            str(here / 'src/art/picotronnest/executionlab/PointerSender.java'),
                            str(here / 'src/art/picotronnest/executionlab/PointerState.java'),
                            str(here / 'src/art/picotronnest/executionlab/RelativePointer.java'),
                            str(here / 'src/art/picotronnest/executionlab/CursorProfile.java'),
                            str(here / 'src/art/picotronnest/executionlab/SessionExit.java'),
                            str(here / 'src/art/picotronnest/executionlab/FrameStream.java'),
                            str(here / 'src/art/picotronnest/executionlab/KeyboardProtocol.java'),
                            str(here / 'src/art/picotronnest/executionlab/KeyboardState.java'),
                            str(here / 'src/art/picotronnest/executionlab/KeyboardWriter.java'),
                            str(here / 'src/art/picotronnest/executionlab/OrderedInputWriter.java'),
                            str(here / ('tests/' + test + '.java'))], check=True, capture_output=True, timeout=30)
            subprocess.run([str(jdk / 'java.exe'), '-cp', destination,
                            'art.picotronnest.executionlab.' + test],
                           check=True, capture_output=True, timeout=10)
