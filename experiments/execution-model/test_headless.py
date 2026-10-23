"""Reject stale/false runtime proof and unsafe preparation inputs."""
import io
import tarfile
from pathlib import Path
import tempfile
import unittest

from check_report import validate
from prepare_headless import regular_files
from provision_runtime import read_owner
from test_check_report import fixture


def headless_fixture():
    report, build, census = fixture()
    report.update(schema=3, session='fresh', linux_guest_sha256='guest', linux_components={},
                  headless_components={'files': {}}, runtime_contract={'version': 'test'}, picotron_execution='ATTEMPTED')
    build.update(linux_components={'packages': {}, 'guests': {'arm64-v8a': 'guest'}},
                 headless_components=report['headless_components'], runtime_contract=report['runtime_contract'])
    for name, code, output in [('linux_guest', 0, 'PICONEST_GUEST_MARKER fresh\nPICONEST_LINUX_GUEST_OK\n'),
                              ('linux_guest_missing_marker', 41, 'PICONEST_MARKER_MISSING\n')]:
        report['cases'].append({'name': name, 'outcome': 'EXITED', 'exit_code': code, 'output_truncated': False,
                                'output': f'PICONEST_LINUX_GUEST pid={300 + code} ppid=250 uid=10001\n' + output})
    for attempt in (1, 2):
        report['cases'].append({'name': f'picotron_headless_{attempt}', 'outcome': 'EXITED', 'exit_code': 0,
                                'output_truncated': False,
                                'output': f'PICONEST_EXEC_PICOTRON pid={400 + attempt} ppid=350 uid=10001\n'
                                          'PICONEST_GLIBC glibc=2.36 sdl=2.32.10\n'
                                          f'PICONEST_PICOTRON_LUA fresh:{attempt}\n'
                                          f'PICONEST_PICOTRON_OK fresh:{attempt}\n'})
    return report, build, census


class HeadlessEvidence(unittest.TestCase):
    def test_complete_evidence(self):
        self.assertEqual(validate(*headless_fixture()), 'arm64-v8a')

    def test_exec_alone_is_not_picotron(self):
        report, build, census = headless_fixture()
        report['cases'][-1]['output'] = report['cases'][-1]['output'].split('PICONEST_PICOTRON_LUA')[0]
        with self.assertRaisesRegex(ValueError, 'current attempt marker'):
            validate(report, build, census)

    def test_repeat_requires_new_marker(self):
        report, build, census = headless_fixture()
        report['cases'][-1]['output'] = report['cases'][-1]['output'].replace('fresh:2', 'fresh:1')
        with self.assertRaisesRegex(ValueError, 'current attempt marker'):
            validate(report, build, census)

    def test_script_marker_before_timeout_does_not_pass(self):
        report, build, census = headless_fixture()
        report['cases'][-1]['exit_code'] = 124
        with self.assertRaisesRegex(ValueError, 'termination failed'):
            validate(report, build, census)

    def test_wrong_runtime_contract(self):
        report, build, census = headless_fixture()
        report['runtime_contract'] = {'version': 'other'}
        with self.assertRaisesRegex(ValueError, 'owner runtime contract'):
            validate(report, build, census)

    def test_wrong_owner_archive_rejected_before_device_access(self):
        import zipfile
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'owner.zip'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('runtime.zip', b'not the owner runtime')
            with self.assertRaisesRegex(ValueError, 'inspected owner package'):
                read_owner(path, {'member': 'runtime.zip', 'archive_sha256': 'wrong'})

    def test_source_traversal_and_case_alias_rejected(self):
        for names in [('../outside',), ('lib/A.h', 'lib/a.h')]:
            with self.subTest(names=names), tempfile.TemporaryDirectory() as directory:
                data = io.BytesIO()
                with tarfile.open(fileobj=data, mode='w') as archive:
                    for name in names:
                        member = tarfile.TarInfo(name)
                        member.size = 1
                        archive.addfile(member, io.BytesIO(b'x'))
                with self.assertRaises(ValueError):
                    regular_files(data.getvalue(), Path(directory))


if __name__ == '__main__':
    unittest.main()
