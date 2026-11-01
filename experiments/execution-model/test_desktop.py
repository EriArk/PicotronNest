import struct
import unittest
from copy import deepcopy
from check_report import validate, validate_frame
from test_headless import headless_fixture


def desktop_fixture():
    report, build, census = headless_fixture()
    build.update(desktop_probe=True, desktop_live=True, desktop_pointer=True)
    report['schema'] = 4
    report['desktop_frames'] = dict(frames=20, sequence=80, drawn_sequence=80,
                                    width=1024, height=768, peer_pid=500, pointer_packets=3)
    report['cases'].append(dict(name='picotron_desktop_trace', outcome='EXITED', exit_code=0,
                               output_truncated=False, output=(
        'PICONEST_TRACE_LOADED pid=500 uid=10001\nPICONEST_PRESENT width=1024 height=768\n'
        'PICONEST_FRAME_CAPTURE sequence=60\nPICONEST_TRACE_QUIT\nPICONEST_TRACE_DONE\n'
        'PICONEST_INPUT_DONE packets=3 downs=1 ups=1 held=0\n')))
    return report, build, census


class DesktopEvidence(unittest.TestCase):
    def test_keyboard_requires_host_peer_and_native_delivery(self):
        report, build, census = desktop_fixture()
        build['desktop_keyboard_bridge'] = True
        report['desktop_keyboard'] = dict(peer_pid=500, packets=4, accepted=4)
        output = report['cases'][-1]['output'] + ('PICONEST_KEYBOARD_CONNECTED\n'
            'PICONEST_KEYBOARD_DONE packets=4 downs=1 ups=1 repeats=1 held=0 events=4 '
            'text_events=1 text_bytes=4 suppressed=0 pending=0 buffered=0 failed=0\n')
        report['cases'][-1]['output'] = output
        self.assertEqual(validate(report, build, census), 'arm64-v8a')
        for old, new in [('ups=1', 'ups=0'), ('held=0', 'held=1'), ('events=4', 'events=3'),
                         ('suppressed=0', 'suppressed=1'), ('pending=0', 'pending=1'),
                         ('buffered=0', 'buffered=7'), ('failed=0', 'failed=1'),
                         ('PICONEST_KEYBOARD_CONNECTED', 'missing')]:
            changed = deepcopy(report)
            changed['cases'][-1]['output'] = output.replace(old, new)
            with self.subTest(change=new), self.assertRaises(ValueError): validate(changed, build, census)
        for host in [None, dict(peer_pid=501, packets=4, accepted=4), dict(peer_pid=500, packets=3, accepted=4),
                     dict(peer_pid=500, packets=True, accepted=4),
                     dict(peer_pid=500, packets=4, accepted=4, error='write failed')]:
            changed = deepcopy(report); changed['desktop_keyboard'] = host
            with self.subTest(host=host), self.assertRaises(ValueError): validate(changed, build, census)
        changed = deepcopy(report); del changed['desktop_keyboard']
        changed['cases'][-1]['output'] = output.replace('PICONEST_KEYBOARD_CONNECTED\n', '')
        with self.assertRaises(ValueError): validate(changed, build, census)
        report['cases'][-1]['output'] += 'PICONEST_KEYBOARD_ERROR code=2\n'
        with self.assertRaises(ValueError): validate(report, build, census)

    def test_live_peer_and_actual_draw_required(self):
        report, build, census = desktop_fixture()
        self.assertEqual(validate(report, build, census), 'arm64-v8a')
        for bad in [dict(peer_pid=999), dict(drawn_sequence=0), dict(drawn_sequence=81),
                    dict(frames=0), dict(error='partial frame')]:
            changed = deepcopy(report)
            changed['desktop_frames'].update(bad)
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                validate(changed, build, census)

    def test_pointer_loss_and_held_button_rejected(self):
        report, build, census = desktop_fixture()
        output = report['cases'][-1]['output']
        for old, new in [('packets=3', 'packets=2'), ('ups=1', 'ups=0'), ('held=0', 'held=1'),
                         ('PICONEST_INPUT_DONE', 'missing'), ('PICONEST_TRACE_DONE',
                          'PICONEST_TRACE_DONE\nPICONEST_INPUT_INVALID')]:
            report['cases'][-1]['output'] = output.replace(old, new)
            with self.subTest(change=new), self.assertRaises(ValueError):
                validate(report, build, census)
        report['cases'][-1]['output'] = output
        report['desktop_frames']['pointer_error'] = 'write failed'
        with self.assertRaises(ValueError):
            validate(report, build, census)

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
