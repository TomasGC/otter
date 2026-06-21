"""Unit tests for the new Manager (cli.manage)."""

from unittest.mock import patch

import pytest
from fake_subprocess import FakeSubprocessRunner

from cli.manage import Manager

pytestmark = pytest.mark.unit


def make_manager():
    runner = FakeSubprocessRunner()
    return Manager(runner), runner


class TestManagerBuild:
    def test_build_dispatches_to_build_action(self):
        mgr, runner = make_manager()
        with patch("cli.manage.BuildAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            rc = mgr.dispatch(["build"])
        MockAction.assert_called_once_with(runner)
        MockAction.return_value.run.assert_called_once_with(install=True)
        assert rc == 0

    def test_build_no_install_flag(self):
        mgr, runner = make_manager()
        with patch("cli.manage.BuildAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["build", "--no-install"])
        MockAction.return_value.run.assert_called_once_with(install=False)

    def test_build_returns_action_exit_code(self):
        mgr, runner = make_manager()
        with patch("cli.manage.BuildAction") as MockAction:
            MockAction.return_value.run.return_value = 1
            rc = mgr.dispatch(["build"])
        assert rc == 1


class TestManagerTest:
    def test_test_dispatches_to_test_action(self):
        mgr, runner = make_manager()
        with patch("cli.manage.TestAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["test"])
        MockAction.assert_called_once_with(runner)
        MockAction.return_value.run.assert_called_once_with(suites=[])

    def test_test_unit_suite(self):
        mgr, runner = make_manager()
        with patch("cli.manage.TestAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["test", "unit"])
        MockAction.return_value.run.assert_called_once_with(suites=["unit"])

    def test_test_multiple_suites(self):
        mgr, runner = make_manager()
        with patch("cli.manage.TestAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["test", "unit", "instrumented"])
        MockAction.return_value.run.assert_called_once_with(suites=["unit", "instrumented"])

    def test_test_coverage_suite(self):
        mgr, runner = make_manager()
        with patch("cli.manage.TestAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["test", "coverage"])
        MockAction.return_value.run.assert_called_once_with(suites=["coverage"])


class TestManagerTestScripts:
    def test_test_scripts_dispatches_to_test_scripts_action(self):
        mgr, runner = make_manager()
        with patch("cli.manage.TestScriptsAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["test-scripts"])
        MockAction.assert_called_once_with(runner)
        MockAction.return_value.run.assert_called_once_with(suites=[])

    def test_test_scripts_unit_suite(self):
        mgr, runner = make_manager()
        with patch("cli.manage.TestScriptsAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["test-scripts", "unit"])
        MockAction.return_value.run.assert_called_once_with(suites=["unit"])

    def test_test_scripts_integration_mock_suite(self):
        mgr, runner = make_manager()
        with patch("cli.manage.TestScriptsAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["test-scripts", "integration-mock"])
        MockAction.return_value.run.assert_called_once_with(suites=["integration-mock"])

    def test_test_scripts_integration_real_suite(self):
        mgr, runner = make_manager()
        with patch("cli.manage.TestScriptsAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["test-scripts", "integration-real"])
        MockAction.return_value.run.assert_called_once_with(suites=["integration-real"])

    def test_test_scripts_coverage_overrides(self):
        mgr, runner = make_manager()
        with patch("cli.manage.TestScriptsAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["test-scripts", "unit", "coverage"])
        MockAction.return_value.run.assert_called_once_with(suites=["unit", "coverage"])

    def test_test_scripts_e2e_suite(self):
        mgr, runner = make_manager()
        with patch("cli.manage.TestScriptsAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["test-scripts", "e2e"])
        MockAction.return_value.run.assert_called_once_with(suites=["e2e"])


class TestManagerCreate:
    def test_create_dispatches_to_create_action(self):
        mgr, runner = make_manager()
        with patch("cli.manage.CreateAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["create"])
        MockAction.assert_called_once_with(runner)
        MockAction.return_value.run.assert_called_once_with(suites=[], output_dir=None)

    def test_create_template_suite(self):
        mgr, runner = make_manager()
        with patch("cli.manage.CreateAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["create", "template"])
        MockAction.return_value.run.assert_called_once_with(suites=["template"], output_dir=None)

    def test_create_archives_suite(self):
        mgr, runner = make_manager()
        with patch("cli.manage.CreateAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["create", "archives"])
        MockAction.return_value.run.assert_called_once_with(suites=["archives"], output_dir=None)

    def test_create_output_dir(self, tmp_path):
        mgr, runner = make_manager()
        with patch("cli.manage.CreateAction") as MockAction:
            MockAction.return_value.run.return_value = 0
            mgr.dispatch(["create", "--output-dir", str(tmp_path)])
        call_kwargs = MockAction.return_value.run.call_args[1]
        assert call_kwargs["output_dir"] == tmp_path


class TestManagerAdb:
    def test_adb_connect_dispatches_to_adb_action(self):
        mgr, runner = make_manager()
        with patch("cli.manage.AdbAction") as MockAction:
            MockAction.return_value.run_connect.return_value = 0
            mgr.dispatch(["adb", "connect"])
        MockAction.assert_called_once_with(runner)
        MockAction.return_value.run_connect.assert_called_once_with(device=None, pair=None, pair_address=None)

    def test_adb_connect_with_device(self):
        mgr, runner = make_manager()
        with patch("cli.manage.AdbAction") as MockAction:
            MockAction.return_value.run_connect.return_value = 0
            mgr.dispatch(["adb", "connect", "--device", "dev:5555"])
        MockAction.return_value.run_connect.assert_called_once_with(device="dev:5555", pair=None, pair_address=None)

    def test_adb_connect_with_pair(self):
        mgr, runner = make_manager()
        with patch("cli.manage.AdbAction") as MockAction:
            MockAction.return_value.run_connect.return_value = 0
            mgr.dispatch(["adb", "connect", "--pair", "123456", "--pair-address", "192.168.1.1:12345"])
        MockAction.return_value.run_connect.assert_called_once_with(
            device=None, pair="123456", pair_address="192.168.1.1:12345"
        )

    def test_adb_send_dispatches_to_adb_action(self):
        mgr, runner = make_manager()
        with patch("cli.manage.AdbAction") as MockAction:
            MockAction.return_value.run_send.return_value = 0
            mgr.dispatch(["adb", "send"])
        MockAction.return_value.run_send.assert_called_once_with(files=None, dest=None, ci=False)

    def test_adb_send_with_files(self, tmp_path):
        mgr, runner = make_manager()
        f = tmp_path / "test.zip"
        f.write_bytes(b"")
        with patch("cli.manage.AdbAction") as MockAction:
            MockAction.return_value.run_send.return_value = 0
            mgr.dispatch(["adb", "send", str(f)])
        call_args = MockAction.return_value.run_send.call_args[1]
        assert len(call_args["files"]) == 1

    def test_adb_send_with_dest(self):
        mgr, runner = make_manager()
        with patch("cli.manage.AdbAction") as MockAction:
            MockAction.return_value.run_send.return_value = 0
            mgr.dispatch(["adb", "send", "--dest", "/sdcard/otter"])
        call_kwargs = MockAction.return_value.run_send.call_args[1]
        assert call_kwargs["dest"] == "/sdcard/otter"

    def test_adb_send_ci_flag(self):
        mgr, runner = make_manager()
        with patch("cli.manage.AdbAction") as MockAction:
            MockAction.return_value.run_send.return_value = 0
            mgr.dispatch(["adb", "send", "--ci"])
        call_kwargs = MockAction.return_value.run_send.call_args[1]
        assert call_kwargs["ci"] is True

    def test_adb_send_empty_files_becomes_none(self):
        mgr, runner = make_manager()
        with patch("cli.manage.AdbAction") as MockAction:
            MockAction.return_value.run_send.return_value = 0
            mgr.dispatch(["adb", "send"])
        call_kwargs = MockAction.return_value.run_send.call_args[1]
        assert call_kwargs["files"] is None
