"""Unit tests for BuildAction."""

from unittest.mock import MagicMock

import pytest

from cli.actions.build import BuildAction
from fake_subprocess import FakeSubprocessRunner

pytestmark = pytest.mark.unit


def make_action(tmp_path):
    runner = FakeSubprocessRunner()
    gradle = MagicMock()
    adb = MagicMock()
    version_mgr = MagicMock()
    connector = MagicMock()
    gradle.run_task.return_value = True
    adb.get_connected.return_value = ["192.168.1.1:5555"]
    connector.auto_connect.return_value = "192.168.1.1:5555"
    version_mgr.increment.return_value = (2, "0.0.2")
    version_mgr.get_apk_path.return_value = tmp_path / "app-debug.apk"
    (tmp_path / "app-debug.apk").write_bytes(b"APK")
    action = BuildAction(runner, gradle, adb, version_mgr, project_root=tmp_path, connector=connector)
    return action, gradle, adb, version_mgr, runner, connector


class TestBuildActionMethods:
    def test_increment_version_delegates_to_version_mgr(self, tmp_path):
        action, _, _, version_mgr, _, _ = make_action(tmp_path)
        code, name = action.increment_version()
        version_mgr.increment.assert_called_once()
        assert code == 2
        assert name == "0.0.2"

    def test_build_apk_calls_assemble_debug(self, tmp_path):
        action, gradle, _, _, _, _ = make_action(tmp_path)
        action.build_apk()
        gradle.run_task.assert_called_once_with("assembleDebug")

    def test_build_apk_uses_variant(self, tmp_path):
        action, gradle, _, _, _, _ = make_action(tmp_path)
        action.build_apk(variant="release")
        gradle.run_task.assert_called_once_with("assembleRelease")

    def test_build_apk_returns_gradle_result(self, tmp_path):
        action, gradle, _, _, _, _ = make_action(tmp_path)
        gradle.run_task.return_value = False
        assert action.build_apk() is False

    def test_get_apk_path_delegates_to_version_mgr(self, tmp_path):
        action, _, _, version_mgr, _, _ = make_action(tmp_path)
        path = action.get_apk_path()
        version_mgr.get_apk_path.assert_called_once_with("debug")
        assert path == tmp_path / "app-debug.apk"


class TestBuildActionRun:
    def test_run_success_with_install(self, tmp_path):
        action, _, adb, _, _, _ = make_action(tmp_path)
        adb.install_apk.return_value = True
        assert action.run(install=True) == 0

    def test_run_success_no_install(self, tmp_path):
        action, _, adb, _, _, _ = make_action(tmp_path)
        assert action.run(install=False) == 0
        adb.install_apk.assert_not_called()

    def test_run_returns_1_when_version_increment_fails(self, tmp_path):
        action, _, _, version_mgr, _, _ = make_action(tmp_path)
        version_mgr.increment.side_effect = RuntimeError("no build.gradle")
        assert action.run() == 1

    def test_run_returns_1_when_build_fails(self, tmp_path):
        action, gradle, _, _, _, _ = make_action(tmp_path)
        gradle.run_task.return_value = False
        assert action.run() == 1

    def test_run_returns_1_when_apk_not_found(self, tmp_path):
        action, _, _, version_mgr, _, _ = make_action(tmp_path)
        version_mgr.get_apk_path.return_value = None
        assert action.run() == 1

    def test_run_auto_connects_when_no_device(self, tmp_path):
        action, _, adb, _, _, connector = make_action(tmp_path)
        adb.get_connected.return_value = []
        connector.auto_connect.return_value = "192.168.1.1:5555"
        adb.install_apk.return_value = True
        assert action.run(install=True) == 0
        connector.auto_connect.assert_called_once()

    def test_run_returns_0_when_no_device_and_install_requested(self, tmp_path):
        action, _, adb, _, _, connector = make_action(tmp_path)
        adb.get_connected.return_value = []
        connector.auto_connect.return_value = None
        assert action.run(install=True) == 0

    def test_run_returns_1_when_install_fails(self, tmp_path):
        action, _, adb, _, _, _ = make_action(tmp_path)
        adb.install_apk.return_value = False
        assert action.run(install=True) == 1

    def test_run_uses_first_connected_device(self, tmp_path):
        action, _, adb, _, _, _ = make_action(tmp_path)
        adb.get_connected.return_value = ["dev1:5555", "dev2:5555"]
        adb.install_apk.return_value = True
        action.run(install=True)
        adb.install_apk.assert_called_once()
        assert adb.install_apk.call_args[0][1] == "dev1:5555"


class TestBuildActionDefaults:
    def test_default_install_is_true(self, tmp_path):
        action, _, adb, _, _, _ = make_action(tmp_path)
        adb.install_apk.return_value = True
        action.run()
        adb.install_apk.assert_called_once()


class TestBuildActionConnectorLazy:
    def test_get_connector_creates_when_none(self, tmp_path):
        from unittest.mock import patch, MagicMock
        runner = FakeSubprocessRunner()
        action = BuildAction(runner, project_root=tmp_path)
        mock_dc = MagicMock()
        with patch("cli.adb_connect.DeviceConnector", return_value=mock_dc):
            result = action._get_connector()
        assert result is mock_dc
