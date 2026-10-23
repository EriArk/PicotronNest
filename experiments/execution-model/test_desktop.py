import struct
import unittest
from check_report import validate, validate_frame
from test_headless import headless_fixture


class DesktopEvidence(unittest.TestCase):
    def test_frame_length_and_bounds(self):
        good = struct.pack('<4I', 0x504e4631, 3, 2, 60) + bytes(24)
        self.assertEqual(validate_frame(good)['stride'], 12)
        for bad in [b'', good[:-1], good + b'X', struct.pack('<4I', 0x504e4631, 0xffffffff, 2, 1)]:
            with self.subTest(raw=bad[:16]), self.assertRaises(ValueError):
                validate_frame(bad)

    def test_desktop_requires_presentation_and_shutdown(self):
        report, build, census = headless_fixture()
        build['desktop_probe'] = True
        report['schema'] = 4
        output = ('PICONEST_TRACE_LOADED pid=500 uid=10001\nPICONEST_PRESENT width=960 height=540\n'
                  'PICONEST_FRAME_CAPTURE sequence=60\nPICONEST_TRACE_QUIT\nPICONEST_TRACE_DONE\n')
        case = {'name': 'picotron_desktop_trace', 'outcome': 'EXITED', 'exit_code': 0,
                'output_truncated': False, 'output': output}
        report['cases'].append(case)
        self.assertEqual(validate(report, build, census), 'arm64-v8a')
        for missing in ['PICONEST_PRESENT ', 'PICONEST_TRACE_QUIT', 'PICONEST_TRACE_LOADED']:
            case['output'] = output.replace(missing, 'absent')
            with self.assertRaises(ValueError):
                validate(report, build, census)
