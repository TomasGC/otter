"""Unit tests for AdbAction."""

from unittest.mock import MagicMock, patch

import pytest

from cli.actions.adb import AdbAction, SUBVERBS
from fake_subprocess import FakeSubprocessRunner

pytestmark = pytest.mark.unit

SETTINGS = {
    "test_archives": {
        "device_path": "/sdcard/otter",
        "host_path": "archives",
        "files": ["test.zip", "test.rpa"],
    },
}


def make_action(tmp_path, settings=None):
    runner = FakeSubprocessRunner()
    action = AdbAction(runner, test_settings=settings or SETTINGS)
    action._project_root = tmp_path
    return action, runner


class TestSubverbsConstant:
    def test_contains_connect_and_send(self):
        assert "connect" in SUBVERBS
        assert "send" in SUBVERBS


class TestRunConnect:
    def test_returns_0_on_successful_connect(self, tmp_path):
        action, _ = make_action(tmp_path)
        mock_connector = MagicMock()
        mock_connector.auto_connect.return_value = "192.168.1.1:5555"
        with patch.object(action, "_get_connector", return_value=mock_connector):
            assert action.run_connect() == 0

    def test_returns_1_when_connect_fails(self, tmp_path):
        action, _ = make_action(tmp_path)
        mock_connector = MagicMock()
        mock_connector.auto_connect.return_value = None
        with patch.object(action, "_get_connector", return_value=mock_connector):
            assert action.run_connect() == 1

    def test_passes_device_arg(self, tmp_path):
        action, _ = make_action(tmp_path)
        mock_connector = MagicMock()
        mock_connector.auto_connect.return_value = "dev:5555"
        with patch.object(action, "_get_connector", return_value=mock_connector):
            action.run_connect(device="dev:5555")
        mock_connector.auto_connect.assert_called_once_with(
            target_device="dev:5555",
            pairing_code=None,
            pairing_address=None,
        )

    def test_passes_pair_args(self, tmp_path):
        action, _ = make_action(tmp_path)
        mock_connector = MagicMock()
        mock_connector.auto_connect.return_value = "dev:5555"
        with patch.object(action, "_get_connector", return_value=mock_connector):
            action.run_connect(pair="123456", pair_address="192.168.1.1:12345")
        mock_connector.auto_connect.assert_called_once_with(
            target_device=None,
            pairing_code="123456",
            pairing_address="192.168.1.1:12345",
        )


class TestRunSend:
    def test_returns_0_on_success(self, tmp_path):
        action, _ = make_action(tmp_path)
        mock_pusher = MagicMock()
        mock_pusher.ensure_connection.return_value = True
        mock_pusher.push_files.return_value = (2, 0)
        with patch.object(action, "_get_pusher", return_value=mock_pusher):
            assert action.run_send(files=["a.zip"], dest="/sdcard/otter") == 0

    def test_returns_1_when_connection_fails(self, tmp_path):
        action, _ = make_action(tmp_path)
        mock_pusher = MagicMock()
        mock_pusher.ensure_connection.return_value = False
        with patch.object(action, "_get_pusher", return_value=mock_pusher):
            assert action.run_send(files=["a.zip"], dest="/sdcard/otter") == 1

    def test_returns_1_when_push_has_failures(self, tmp_path):
        action, _ = make_action(tmp_path)
        mock_pusher = MagicMock()
        mock_pusher.ensure_connection.return_value = True
        mock_pusher.push_files.return_value = (0, 1)
        with patch.object(action, "_get_pusher", return_value=mock_pusher):
            assert action.run_send(files=["a.zip"], dest="/sdcard/otter") == 1

    def test_loads_settings_when_dest_none(self, tmp_path):
        action, _ = make_action(tmp_path)
        mock_pusher = MagicMock()
        mock_pusher.ensure_connection.return_value = True
        mock_pusher.push_files.return_value = (0, 0)
        with patch.object(action, "_get_pusher", return_value=mock_pusher):
            action.run_send(files=["a.zip"], dest=None)
        assert mock_pusher.push_files.call_args[0][1] == "/sdcard/otter"

    def test_loads_settings_when_files_none(self, tmp_path):
        action, _ = make_action(tmp_path)
        mock_pusher = MagicMock()
        mock_pusher.ensure_connection.return_value = True
        mock_pusher.push_files.return_value = (0, 0)
        with patch.object(action, "_get_pusher", return_value=mock_pusher):
            action.run_send(files=None, dest="/sdcard/otter")
        files = mock_pusher.push_files.call_args[0][0]
        assert len(files) == 2

    def test_ci_flag_passed_to_ensure_connection(self, tmp_path):
        action, _ = make_action(tmp_path)
        mock_pusher = MagicMock()
        mock_pusher.ensure_connection.return_value = True
        mock_pusher.push_files.return_value = (0, 0)
        with patch.object(action, "_get_pusher", return_value=mock_pusher):
            action.run_send(files=["a.zip"], dest="/sdcard/otter", ci=True)
        mock_pusher.ensure_connection.assert_called_once_with(non_interactive=True)


class TestLazyCreation:
    def test_get_settings_loads_when_not_injected(self):
        from unittest.mock import patch
        runner = FakeSubprocessRunner()
        action = AdbAction(runner)  # no test_settings
        with patch("cli.actions.adb.load_test_settings", return_value=SETTINGS):
            result = action._get_settings()
        assert result == SETTINGS

    def test_get_connector_creates_device_connector(self, tmp_path):
        from unittest.mock import patch, MagicMock
        runner = FakeSubprocessRunner()
        action = AdbAction(runner)
        action._project_root = tmp_path
        mock_dc = MagicMock()
        with patch("cli.adb_connect.DeviceConnector", return_value=mock_dc):
            result = action._get_connector()
        assert result is mock_dc

    def test_get_pusher_creates_file_pusher(self):
        from unittest.mock import patch, MagicMock
        runner = FakeSubprocessRunner()
        action = AdbAction(runner)
        mock_pusher = MagicMock()
        with patch("cli.send_to_phone.FilePusher", return_value=mock_pusher):
            result = action._get_pusher()
        assert result is mock_pusher
