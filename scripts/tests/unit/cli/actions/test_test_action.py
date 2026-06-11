"""Unit tests for TestAction."""

from unittest.mock import MagicMock

import pytest
from fake_subprocess import FakeSubprocessRunner

from cli.actions.test import SUITES
from cli.actions.test import TestAction as AndroidTestAction

pytestmark = pytest.mark.unit

SETTINGS = {
    "test_archives": {
        "device_path": "/sdcard/otter",
        "host_path": "archives",
        "files": ["test.zip", "test.rpa"],
    },
    "test_execution": {
        "instrumented_timeout_seconds": 600,
    },
}


def make_action(tmp_path, settings=None):
    runner = FakeSubprocessRunner()
    gradle = MagicMock()
    adb = MagicMock()
    connector = MagicMock()
    gradle.run_task.return_value = True
    adb.get_connected.return_value = ["192.168.1.1:5555"]
    connector.auto_connect.return_value = "192.168.1.1:5555"
    action = AndroidTestAction(
        runner,
        gradle,
        adb,
        project_root=tmp_path,
        test_settings=settings or SETTINGS,
        connector=connector,
    )
    return action, gradle, adb, runner, connector


class TestSuitesConstant:
    def test_contains_expected_suites(self):
        assert "unit" in SUITES
        assert "instrumented" in SUITES
        assert "coverage" in SUITES


class TestRunUnit:
    def test_calls_unit_task(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        action.run_unit()
        gradle.run_task.assert_called_once_with("testDebugUnitTest")

    def test_calls_coverage_task_when_flag_set(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        action.run_unit(coverage=True)
        gradle.run_task.assert_called_once_with("testDebugUnitTestCoverage")

    def test_returns_true_on_success(self, tmp_path):
        action, _, _, _, _ = make_action(tmp_path)
        assert action.run_unit() is True

    def test_returns_false_on_failure(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        gradle.run_task.return_value = False
        assert action.run_unit() is False


class TestSendArchives:
    def test_sends_existing_files(self, tmp_path):
        archives_dir = tmp_path / "archives"
        archives_dir.mkdir()
        (archives_dir / "test.zip").write_bytes(b"PK")
        (archives_dir / "test.rpa").write_bytes(b"RPA")

        settings = {**SETTINGS, "test_archives": {**SETTINGS["test_archives"], "host_path": str(archives_dir)}}
        action, _, _, runner, _ = make_action(tmp_path, settings)
        runner.add_run(returncode=0)  # mkdir
        runner.add_run(returncode=0)  # push zip
        runner.add_run(returncode=0)  # push rpa

        sent = action.send_archives("192.168.1.1:5555")
        assert sent == 2

    def test_skips_missing_files(self, tmp_path):
        archives_dir = tmp_path / "archives"
        archives_dir.mkdir()

        settings = {**SETTINGS, "test_archives": {**SETTINGS["test_archives"], "host_path": str(archives_dir)}}
        action, _, _, runner, _ = make_action(tmp_path, settings)
        runner.add_run(returncode=0)  # mkdir

        sent = action.send_archives("192.168.1.1:5555")
        assert sent == 0

    def test_push_failure_not_counted(self, tmp_path):
        archives_dir = tmp_path / "archives"
        archives_dir.mkdir()
        (archives_dir / "test.zip").write_bytes(b"PK")
        settings = {
            **SETTINGS,
            "test_archives": {**SETTINGS["test_archives"], "host_path": str(archives_dir), "files": ["test.zip"]},
        }
        action, _, _, runner, _ = make_action(tmp_path, settings)
        runner.add_run(returncode=0)  # mkdir
        runner.add_run(returncode=1)  # push fails

        sent = action.send_archives("192.168.1.1:5555")
        assert sent == 0


class TestRunInstrumented:
    def test_calls_connected_task(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        runner.add_run(returncode=0)  # mkdir in send_archives
        action.run_instrumented("192.168.1.1:5555")
        gradle.run_task.assert_called_with("connectedDebugAndroidTest", timeout=600)

    def test_returns_gradle_result(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        runner.add_run(returncode=0)
        gradle.run_task.return_value = False
        assert action.run_instrumented("192.168.1.1:5555") is False


class TestTestActionRun:
    def test_no_suites_runs_both(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        runner.add_run(returncode=0)
        action.run()
        assert gradle.run_task.call_count == 2

    def test_unit_suite_runs_only_unit(self, tmp_path):
        action, gradle, adb, _, _ = make_action(tmp_path)
        action.run(suites=["unit"])
        gradle.run_task.assert_called_once_with("testDebugUnitTest")
        adb.get_connected.assert_not_called()

    def test_instrumented_suite_runs_only_instrumented(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        runner.add_run(returncode=0)
        action.run(suites=["instrumented"])
        gradle.run_task.assert_called_with("connectedDebugAndroidTest", timeout=600)

    def test_coverage_overrides_other_suites(self, tmp_path):
        action, gradle, adb, _, _ = make_action(tmp_path)
        action.run(suites=["unit", "instrumented", "coverage"])
        gradle.run_task.assert_called_once_with("testDebugUnitTestCoverage")
        adb.get_connected.assert_not_called()

    def test_coverage_alone_runs_coverage_task(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        rc = action.run(suites=["coverage"])
        gradle.run_task.assert_called_once_with("testDebugUnitTestCoverage")
        assert rc == 0

    def test_coverage_returns_1_on_failure(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        gradle.run_task.return_value = False
        assert action.run(suites=["coverage"]) == 1

    def test_returns_0_on_full_success(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        runner.add_run(returncode=0)
        assert action.run() == 0

    def test_returns_1_when_unit_fails(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        gradle.run_task.return_value = False
        assert action.run(suites=["unit"]) == 1

    def test_returns_1_when_no_device_available(self, tmp_path):
        action, _, adb, _, connector = make_action(tmp_path)
        adb.get_connected.return_value = []
        connector.auto_connect.return_value = None
        assert action.run(suites=["instrumented"]) == 1

    def test_auto_connects_when_no_device(self, tmp_path):
        action, gradle, adb, runner, connector = make_action(tmp_path)
        adb.get_connected.side_effect = [[], ["192.168.1.1:5555"]]
        runner.add_run(returncode=0)
        action.run(suites=["instrumented"])
        connector.auto_connect.assert_called_once()

    def test_returns_1_when_device_connects_but_disappears(self, tmp_path):
        action, _, adb, _, connector = make_action(tmp_path)
        adb.get_connected.side_effect = [[], []]
        connector.auto_connect.return_value = "192.168.1.1:5555"
        assert action.run(suites=["instrumented"]) == 1

    def test_empty_suites_same_as_no_suites(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        runner.add_run(returncode=0)
        action.run(suites=[])
        assert gradle.run_task.call_count == 2


class TestLazyCreation:
    def test_get_connector_creates_when_none(self, tmp_path):
        from unittest.mock import MagicMock, patch

        runner = FakeSubprocessRunner()
        action = AndroidTestAction(runner, project_root=tmp_path)
        mock_dc = MagicMock()
        with patch("cli.adb_connect.DeviceConnector", return_value=mock_dc):
            result = action._get_connector()
        assert result is mock_dc

    def test_get_settings_loads_when_not_injected(self):
        from unittest.mock import patch

        runner = FakeSubprocessRunner()
        action = AndroidTestAction(runner)
        with patch("cli.actions.test.load_test_settings", return_value=SETTINGS):
            result = action._get_settings()
        assert result == SETTINGS


class TestInstrumentedFailure:
    def test_returns_1_when_instrumented_fails_with_device_present(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        runner.add_run(returncode=0)  # send_archives mkdir
        gradle.run_task.side_effect = [True, False]  # unit OK, instrumented fails
        assert action.run() == 1
