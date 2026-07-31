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
    adb.get_running_emulators.return_value = []
    adb.list_avds.return_value = []
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
        assert "integration-mocks" in SUITES
        assert "integration-reals" in SUITES
        assert "integrations" in SUITES
        assert "coverage" not in SUITES
        assert "all" not in SUITES


class TestRunUnit:
    def test_calls_three_stages(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        action.run_unit()
        assert gradle.run_task.call_count == 3
        for call, expected_type in zip(
            gradle.run_task.call_args_list,
            ["unit-domain-service", "unit-data", "unit-ui"],
        ):
            assert call.args == ("testDebugUnitTest",)
            assert call.kwargs["extra_args"] == [f"-DtestType={expected_type}"]

    def test_returns_true_on_success(self, tmp_path):
        action, _, _, _, _ = make_action(tmp_path)
        assert action.run_unit() is True

    def test_returns_false_on_failure(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        gradle.run_task.return_value = False
        assert action.run_unit() is False


class TestRunIntegrationMocks:
    def test_calls_two_stages(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        action.run_integration_mocks()
        assert gradle.run_task.call_count == 2
        types = [c.kwargs["extra_args"][0] for c in gradle.run_task.call_args_list]
        assert types == [
            "-DtestType=integration-mock-extractor",
            "-DtestType=integration-mock-other",
        ]

    def test_returns_false_if_any_stage_fails(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        gradle.run_task.side_effect = [False, True]
        assert action.run_integration_mocks() is False

    def test_returns_true_when_all_pass(self, tmp_path):
        action, _, _, _, _ = make_action(tmp_path)
        assert action.run_integration_mocks() is True


class TestRunIntegrationReals:
    def test_calls_single_stage(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        action.run_integration_reals()
        gradle.run_task.assert_called_once_with("testDebugUnitTest", extra_args=["-DtestType=integration-real"])

    def test_returns_false_on_failure(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        gradle.run_task.return_value = False
        assert action.run_integration_reals() is False

    def test_returns_true_on_success(self, tmp_path):
        action, _, _, _, _ = make_action(tmp_path)
        assert action.run_integration_reals() is True


class TestSendArchives:
    def test_sends_existing_files(self, tmp_path):
        archives_dir = tmp_path / "archives"
        archives_dir.mkdir()
        (archives_dir / "test.zip").write_bytes(b"PK")
        (archives_dir / "test.rpa").write_bytes(b"RPA")

        settings = {
            **SETTINGS,
            "test_archives": {
                **SETTINGS["test_archives"],
                "host_path": str(archives_dir),
            },
        }
        action, _, _, runner, _ = make_action(tmp_path, settings)
        runner.add_run(returncode=0)  # mkdir
        runner.add_run(returncode=0)  # push zip
        runner.add_run(returncode=0)  # push rpa

        sent = action.send_archives("192.168.1.1:5555")
        assert sent == 2

    def test_skips_missing_files(self, tmp_path):
        archives_dir = tmp_path / "archives"
        archives_dir.mkdir()

        settings = {
            **SETTINGS,
            "test_archives": {
                **SETTINGS["test_archives"],
                "host_path": str(archives_dir),
            },
        }
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
            "test_archives": {
                **SETTINGS["test_archives"],
                "host_path": str(archives_dir),
                "files": ["test.zip"],
            },
        }
        action, _, _, runner, _ = make_action(tmp_path, settings)
        runner.add_run(returncode=0)  # mkdir
        runner.add_run(returncode=1)  # push fails

        sent = action.send_archives("192.168.1.1:5555")
        assert sent == 0

    def test_sends_glob_files_when_specified(self, tmp_path):
        archives_dir = tmp_path / "archives"
        archives_dir.mkdir()
        (archives_dir / "split.7z.001").write_bytes(b"7Z")
        (archives_dir / "split.7z.002").write_bytes(b"7Z")
        settings = {
            **SETTINGS,
            "test_archives": {
                **SETTINGS["test_archives"],
                "host_path": str(archives_dir),
                "files": [],
                "glob_files": ["split.7z.*"],
            },
        }
        action, _, _, runner, _ = make_action(tmp_path, settings)
        runner.add_run(returncode=0)  # mkdir
        runner.add_run(returncode=0)  # push 001
        runner.add_run(returncode=0)  # push 002

        sent = action.send_archives("192.168.1.1:5555")
        assert sent == 2


class TestRunInstrumented:
    def test_calls_connected_task(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        action._grant_manage_external_storage = MagicMock(return_value=True)
        runner.add_run(returncode=0)  # mkdir in send_archives
        action.run_instrumented("192.168.1.1:5555")
        assert gradle.run_task.call_count == 2
        main_call, isolated_call = gradle.run_task.call_args_list
        assert main_call.args == ("connectedDebugAndroidTest",)
        assert main_call.kwargs["timeout"] == 600
        assert "notClass=" in main_call.kwargs["extra_args"][0]
        assert isolated_call.args == ("connectedDebugAndroidTest",)
        assert "class=" in isolated_call.kwargs["extra_args"][0]

    def test_returns_gradle_result(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        action._grant_manage_external_storage = MagicMock(return_value=True)
        runner.add_run(returncode=0)
        gradle.run_task.return_value = False
        assert action.run_instrumented("192.168.1.1:5555") is False

    def test_returns_false_when_grant_fails(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        action._grant_manage_external_storage = MagicMock(return_value=False)
        assert action.run_instrumented("192.168.1.1:5555") is False
        gradle.run_task.assert_not_called()


class TestRunPermissionIsolatedTest:
    def test_brackets_gradle_run_with_revoke_and_grant(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        runner.add_run(returncode=0)  # pm revoke
        runner.add_run(returncode=0)  # pm grant
        result = action.run_permission_isolated_test("192.168.1.1:5555", 600)

        assert result is True
        revoke_call, grant_call = runner.calls
        assert revoke_call[:4] == ["adb", "-s", "192.168.1.1:5555", "shell"]
        assert "revoke" in revoke_call
        assert grant_call[:4] == ["adb", "-s", "192.168.1.1:5555", "shell"]
        assert "grant" in grant_call

    def test_grant_still_runs_when_gradle_task_fails(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        runner.add_run(returncode=0)  # pm revoke
        runner.add_run(returncode=0)  # pm grant
        gradle.run_task.return_value = False

        result = action.run_permission_isolated_test("192.168.1.1:5555", 600)

        assert result is False
        assert len(runner.calls) == 2  # revoke and grant both still ran


class TestTestActionRun:
    def test_no_suites_runs_all(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        action._grant_manage_external_storage = MagicMock(return_value=True)
        runner.add_run(returncode=0)
        action.run()
        # 3 unit + 2 integ-mock + 1 integ-real + 2 instrumented
        assert gradle.run_task.call_count == 8

    def test_no_args_runs_all(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        action._grant_manage_external_storage = MagicMock(return_value=True)
        runner.add_run(returncode=0)
        action.run()
        assert gradle.run_task.call_count == 8

    def test_unit_suite_runs_only_unit(self, tmp_path):
        action, gradle, adb, _, _ = make_action(tmp_path)
        action.run(suites=["unit"])
        assert gradle.run_task.call_count == 3
        for call, expected_type in zip(
            gradle.run_task.call_args_list,
            ["unit-domain-service", "unit-data", "unit-ui"],
        ):
            assert call.args == ("testDebugUnitTest",)
            assert call.kwargs["extra_args"] == [f"-DtestType={expected_type}"]
        adb.get_connected.assert_not_called()

    def test_instrumented_suite_runs_only_instrumented(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        action._grant_manage_external_storage = MagicMock(return_value=True)
        runner.add_run(returncode=0)
        action.run(suites=["instrumented"])
        assert gradle.run_task.call_count == 2
        for call in gradle.run_task.call_args_list:
            assert call.args == ("connectedDebugAndroidTest",)
            assert call.kwargs["timeout"] == 600

    def test_integrations_suite_runs_mocks_and_reals(self, tmp_path):
        action, gradle, adb, _, _ = make_action(tmp_path)
        action.run(suites=["integrations"])
        assert gradle.run_task.call_count == 3  # 2 mock + 1 real
        adb.get_connected.assert_not_called()

    def test_integration_mocks_suite_runs_only_mocks(self, tmp_path):
        action, gradle, adb, _, _ = make_action(tmp_path)
        action.run(suites=["integration-mocks"])
        assert gradle.run_task.call_count == 2
        adb.get_connected.assert_not_called()

    def test_integration_reals_suite_runs_only_reals(self, tmp_path):
        action, gradle, adb, _, _ = make_action(tmp_path)
        action.run(suites=["integration-reals"])
        assert gradle.run_task.call_count == 1
        adb.get_connected.assert_not_called()

    def test_returns_0_on_full_success(self, tmp_path):
        action, gradle, _, runner, _ = make_action(tmp_path)
        action._grant_manage_external_storage = MagicMock(return_value=True)
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
        action._grant_manage_external_storage = MagicMock(return_value=True)
        adb.get_connected.side_effect = [[], ["192.168.1.1:5555"]]
        runner.add_run(returncode=0)
        action.run(suites=["instrumented"])
        connector.auto_connect.assert_called_once()

    def test_returns_1_when_device_connects_but_disappears(self, tmp_path):
        action, _, adb, _, connector = make_action(tmp_path)
        adb.get_connected.side_effect = [[], []]
        connector.auto_connect.return_value = "192.168.1.1:5555"
        assert action.run(suites=["instrumented"]) == 1


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
        action._grant_manage_external_storage = MagicMock(return_value=True)
        runner.add_run(returncode=0)  # send_archives mkdir
        # 3 unit + 2 integ-mock + 1 integ-real all OK; instrumented main fails, isolated OK
        gradle.run_task.side_effect = [True, True, True, True, True, True, False, True]
        assert action.run() == 1


class TestGrantManageExternalStorage:
    def _make_apks(self, tmp_path) -> None:
        for rel in (
            "app/build/outputs/apk/debug/app-debug.apk",
            "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
        ):
            p = tmp_path / rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(b"fake")

    def test_grants_appop_after_successful_install(self, tmp_path):
        action, gradle, adb, runner, _ = make_action(tmp_path)
        self._make_apks(tmp_path)
        gradle.run_task.return_value = True
        adb.install_apk.return_value = True
        runner.add_run(returncode=0)  # appops set
        result = action._grant_manage_external_storage("emulator-5554")
        assert result is True
        assert gradle.run_task.call_count == 2  # assembleDebug + assembleDebugAndroidTest
        assert adb.install_apk.call_count == 2  # main + test APK

    def test_returns_false_when_build_apk_fails(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        gradle.run_task.return_value = False
        assert action._grant_manage_external_storage("emulator-5554") is False

    def test_returns_false_when_build_test_apk_fails(self, tmp_path):
        action, gradle, _, _, _ = make_action(tmp_path)
        gradle.run_task.side_effect = [True, False]
        assert action._grant_manage_external_storage("emulator-5554") is False

    def test_returns_false_when_main_apk_install_fails(self, tmp_path):
        action, gradle, adb, _, _ = make_action(tmp_path)
        self._make_apks(tmp_path)
        gradle.run_task.return_value = True
        adb.install_apk.return_value = False
        assert action._grant_manage_external_storage("emulator-5554") is False

    def test_returns_false_when_appops_command_fails(self, tmp_path):
        action, gradle, adb, runner, _ = make_action(tmp_path)
        self._make_apks(tmp_path)
        gradle.run_task.return_value = True
        adb.install_apk.return_value = True
        runner.add_run(returncode=1)  # appops set fails
        assert action._grant_manage_external_storage("emulator-5554") is False


class TestEnsureEmulator:
    def test_returns_running_emulator_when_ready(self, tmp_path):
        action, _, adb, _, _ = make_action(tmp_path)
        adb.get_running_emulators.return_value = ["emulator-5554"]
        adb.wait_for_emulator.return_value = "emulator-5554"
        result = action._ensure_emulator()
        assert result == "emulator-5554"
        adb.wait_for_emulator.assert_called_once_with()

    def test_returns_none_when_running_emulator_not_ready_in_time(self, tmp_path):
        action, _, adb, _, _ = make_action(tmp_path)
        adb.get_running_emulators.return_value = ["emulator-5554"]
        adb.wait_for_emulator.return_value = None
        assert action._ensure_emulator() is None

    def test_starts_avd_when_no_running_emulator(self, tmp_path):
        action, _, adb, _, _ = make_action(tmp_path)
        adb.get_running_emulators.return_value = []
        adb.list_avds.return_value = ["Pixel_6_API_34"]
        adb.start_emulator.return_value = True
        adb.wait_for_emulator.return_value = "emulator-5554"
        result = action._ensure_emulator()
        adb.start_emulator.assert_called_once_with("Pixel_6_API_34")
        assert result == "emulator-5554"

    def test_returns_none_when_no_avds(self, tmp_path):
        action, _, adb, _, _ = make_action(tmp_path)
        adb.get_running_emulators.return_value = []
        adb.list_avds.return_value = []
        assert action._ensure_emulator() is None
        adb.start_emulator.assert_not_called()

    def test_returns_none_when_start_emulator_fails(self, tmp_path):
        action, _, adb, _, _ = make_action(tmp_path)
        adb.get_running_emulators.return_value = []
        adb.list_avds.return_value = ["Pixel_6_API_34"]
        adb.start_emulator.return_value = False
        assert action._ensure_emulator() is None
        adb.wait_for_emulator.assert_not_called()

    def test_run_uses_emulator_when_no_physical_device(self, tmp_path):
        action, gradle, adb, runner, connector = make_action(tmp_path)
        adb.get_connected.return_value = []
        connector.auto_connect.return_value = None
        adb.get_running_emulators.return_value = ["emulator-5554"]
        adb.wait_for_emulator.return_value = "emulator-5554"
        action._grant_manage_external_storage = MagicMock(return_value=True)
        runner.add_run(returncode=0)
        rc = action.run(suites=["instrumented"])
        assert rc == 0
        gradle.run_task.assert_called()

    def test_starts_first_avd_when_multiple_avds(self, tmp_path):
        action, _, adb, _, _ = make_action(tmp_path)
        adb.get_running_emulators.return_value = []
        adb.list_avds.return_value = ["Pixel_6_API_34", "Pixel_8_API_35"]
        adb.start_emulator.return_value = True
        adb.wait_for_emulator.return_value = "emulator-5554"
        action._ensure_emulator()
        adb.start_emulator.assert_called_once_with("Pixel_6_API_34")

    def test_run_returns_1_when_no_device_at_all(self, tmp_path):
        action, _, adb, _, connector = make_action(tmp_path)
        adb.get_connected.return_value = []
        connector.auto_connect.return_value = None
        adb.get_running_emulators.return_value = []
        adb.list_avds.return_value = []
        rc = action.run(suites=["instrumented"])
        assert rc == 1
