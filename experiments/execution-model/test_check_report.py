"""Negative evidence tests: callbacks and exit codes alone must not certify execution."""
import unittest
from check_report import PACKAGE, validate


def fixture():
    report = {'phase': 'COMPLETE', 'target_sdk': 36, 'fixture_sha256': 'owned-fixture',
              'abis': ['arm64-v8a'], 'host_pid': 100, 'host_uid': 10001,
              'picotron_execution': 'NOT TESTED', 'cases': []}
    names = ['packaged_identity', 'private_direct', 'private_system_linker', 'packaged_ptrace', 'owned_timeout']
    for index, name in enumerate(names):
        case = {'name': name, 'outcome': 'EXITED', 'exit_code': 0, 'output_truncated': False,
                'output': f'PICONEST_EXEC_OK pid={200 + index} ppid=100 uid=10001\n'
                          'selinux=u:r:untrusted_app:s0:c1\nseccomp=2\n'}
        if name == 'private_direct':
            case = {'name': name, 'outcome': 'START_FAILED', 'error': 'error=13, Permission denied'}
        if name == 'packaged_ptrace':
            case['output'] += 'PICONEST_PTRACE_OK child_exit=37\n'
        if name == 'owned_timeout':
            case.update(outcome='TIMEOUT', exit_code=143, ready_observed=True)
            case['output'] += 'PICONEST_HANG_READY\n'
        report['cases'].append(case)
    build = {'fixture_sha256': {'arm64-v8a': 'owned-fixture'}}
    census = f'PID PPID UID NAME\n100 1 10001 {PACKAGE}\n'
    return report, build, census


class EvidenceValidation(unittest.TestCase):
    def test_control_fixture(self):
        self.assertEqual(validate(*fixture()), 'arm64-v8a')

    def test_exit_without_native_marker_is_rejected(self):
        report, build, census = fixture()
        report['cases'][0]['output'] = ''
        with self.assertRaisesRegex(ValueError, 'without native process marker'):
            validate(report, build, census)

    def test_wrong_uid_is_rejected(self):
        report, build, census = fixture()
        report['cases'][0]['output'] = report['cases'][0]['output'].replace('uid=10001', 'uid=2000')
        with self.assertRaisesRegex(ValueError, 'ownership'):
            validate(report, build, census)

    def test_orphan_blocks_success(self):
        report, build, census = fixture()
        with self.assertRaisesRegex(ValueError, 'child remains'):
            validate(report, build, census + '999 1 10001 orphan\n')

    def test_wrong_build_is_rejected(self):
        report, build, census = fixture()
        report['fixture_sha256'] = 'old-build'
        with self.assertRaisesRegex(ValueError, 'fixture hash'):
            validate(report, build, census)

    def test_missing_binary_is_not_permission_evidence(self):
        report, build, census = fixture()
        report['cases'][1]['error'] = 'error=2, No such file'
        with self.assertRaisesRegex(ValueError, 'unexpected reason'):
            validate(report, build, census)

    def test_malformed_census_is_not_idle(self):
        report, build, census = fixture()
        with self.assertRaisesRegex(ValueError, 'incomplete process census'):
            validate(report, build, census + 'truncated-row\n')

    def test_running_report_is_not_complete(self):
        report, build, census = fixture()
        report['phase'] = 'RUNNING'
        with self.assertRaisesRegex(ValueError, 'did not complete'):
            validate(report, build, census)

    def test_killed_before_ready_is_not_timeout_proof(self):
        report, build, census = fixture()
        report['cases'][4]['ready_observed'] = False
        with self.assertRaisesRegex(ValueError, 'running hang fixture'):
            validate(report, build, census)

    def test_guest_requires_current_root_marker(self):
        report, build, census = fixture()
        report.update(schema=2, session='current-session', linux_guest_sha256='guest', linux_components={})
        build['linux_components'] = {'packages': {}, 'guests': {'arm64-v8a': 'guest'}}
        for name, code, output in [('linux_guest', 0, 'PICONEST_GUEST_MARKER old-session\nPICONEST_LINUX_GUEST_OK\n'),
                                  ('linux_guest_missing_marker', 41, 'PICONEST_MARKER_MISSING\n')]:
            report['cases'].append({'name': name, 'outcome': 'EXITED', 'exit_code': code,
                                    'output_truncated': False,
                                    'output': f'PICONEST_LINUX_GUEST pid={300 + code} ppid=250 uid=10001\n' + output})
        with self.assertRaisesRegex(ValueError, 'stale session marker'):
            validate(report, build, census)
        report['cases'][5]['output'] = report['cases'][5]['output'].replace('old-session', 'current-session')
        self.assertEqual(validate(report, build, census), 'arm64-v8a')
        report['cases'][6]['exit_code'] = 0
        with self.assertRaisesRegex(ValueError, 'guest termination failed'):
            validate(report, build, census)


if __name__ == '__main__':
    unittest.main()
