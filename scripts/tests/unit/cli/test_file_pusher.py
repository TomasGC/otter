"""Unit tests for FilePusher — fake subprocess runner, no real ADB."""

from pathlib import Path
from unittest.mock import MagicMock

import pytest

from cli.send_to_phone import FilePusher
from fake_subprocess import FakeSubprocessRunner


# ---------------------------------------------------------------------------
# ensure_connection
# ---------------------------------------------------------------------------

class TestEnsureConnection:
    def test_returns_true_when_connector_succeeds(self):
        connector = MagicMock()
        connector.auto_connect.return_value = "192.168.1.1:5555"
        pusher = FilePusher(FakeSubprocessRunner(), connector=connector)
        assert pusher.ensure_connection() is True

    def test_returns_false_when_connector_fails_non_interactive(self):
        connector = MagicMock()
        connector.auto_connect.return_value = None
        pusher = FilePusher(FakeSubprocessRunner(), connector=connector)
        assert pusher.ensure_connection(non_interactive=True) is False

    def test_returns_false_when_connector_fails_interactive(self):
        connector = MagicMock()
        connector.auto_connect.return_value = None
        pusher = FilePusher(FakeSubprocessRunner(), connector=connector)
        assert pusher.ensure_connection(non_interactive=False) is False

    def test_calls_auto_connect_on_connector(self):
        connector = MagicMock()
        connector.auto_connect.return_value = "192.168.1.1:5555"
        pusher = FilePusher(FakeSubprocessRunner(), connector=connector)
        pusher.ensure_connection()
        connector.auto_connect.assert_called_once()

    def test_non_interactive_prints_message_on_failure(self, capsys):
        connector = MagicMock()
        connector.auto_connect.return_value = None
        pusher = FilePusher(FakeSubprocessRunner(), connector=connector)
        pusher.ensure_connection(non_interactive=True)
        out = capsys.readouterr().out
        assert "not connected" in out.lower() or "run manually" in out.lower()


# ---------------------------------------------------------------------------
# push_file
# ---------------------------------------------------------------------------

class TestPushFile:
    def test_returns_false_when_file_missing(self, tmp_path):
        runner = FakeSubprocessRunner()
        pusher = FilePusher(runner)
        assert pusher.push_file(tmp_path / "missing.zip", "/sdcard/otter") is False
        assert runner.call_count == 0

    def test_skips_if_already_on_device(self, tmp_path):
        f = tmp_path / "archive.zip"
        f.write_bytes(b"PK\x03\x04")

        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0, stdout="exists")  # check call

        pusher = FilePusher(runner)
        result = pusher.push_file(f, "/sdcard/otter")

        assert result is True
        assert runner.call_count == 1
        assert "test -f" in runner.calls[0][2]

    def test_mkdir_failure_returns_false(self, tmp_path):
        f = tmp_path / "archive.zip"
        f.write_bytes(b"PK\x03\x04")

        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0, stdout="missing")  # check → not found
        runner.add_run(returncode=1, stderr="permission denied")  # mkdir → fail

        pusher = FilePusher(runner)
        result = pusher.push_file(f, "/sdcard/otter")

        assert result is False
        assert "mkdir -p" in runner.calls[1][2]

    def test_push_success(self, tmp_path):
        f = tmp_path / "archive.zip"
        f.write_bytes(b"PK\x03\x04")

        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0, stdout="missing")  # check
        runner.add_run(returncode=0)                    # mkdir
        runner.set_popen(["100% /sdcard/otter/archive.zip\n"], returncode=0)

        pusher = FilePusher(runner)
        result = pusher.push_file(f, "/sdcard/otter")

        assert result is True
        assert runner.called_with("adb", "push")

    def test_push_failure(self, tmp_path):
        f = tmp_path / "archive.zip"
        f.write_bytes(b"PK\x03\x04")

        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0, stdout="missing")  # check
        runner.add_run(returncode=0)                    # mkdir
        runner.set_popen(["error: device offline\n"], returncode=1)

        pusher = FilePusher(runner)
        result = pusher.push_file(f, "/sdcard/otter")

        assert result is False

    def test_push_with_empty_output_lines(self, tmp_path):
        f = tmp_path / "archive.zip"
        f.write_bytes(b"PK\x03\x04")

        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0, stdout="missing")  # check
        runner.add_run(returncode=0)                    # mkdir
        runner.set_popen(["\n", "100% /sdcard/otter/archive.zip\n", "\n"], returncode=0)

        pusher = FilePusher(runner)
        result = pusher.push_file(f, "/sdcard/otter")

        assert result is True


# ---------------------------------------------------------------------------
# push_files
# ---------------------------------------------------------------------------

class TestPushFiles:
    def test_returns_counts(self, tmp_path):
        f1 = tmp_path / "a.zip"
        f2 = tmp_path / "b.zip"
        f1.write_bytes(b"PK\x03\x04")
        f2.write_bytes(b"PK\x03\x04")

        runner = FakeSubprocessRunner()
        # File 1: check→exists (skip = success)
        runner.add_run(returncode=0, stdout="exists")
        # File 2: check→missing, mkdir OK, push OK
        runner.add_run(returncode=0, stdout="missing")
        runner.add_run(returncode=0)
        runner.set_popen(["100%\n"], returncode=0)

        pusher = FilePusher(runner)
        success, fail = pusher.push_files([f1, f2], "/sdcard/otter")

        assert success == 2
        assert fail == 0

    def test_counts_failures(self, tmp_path):
        f1 = tmp_path / "a.zip"
        f2 = tmp_path / "b.zip"
        f1.write_bytes(b"PK\x03\x04")
        # f2 missing from disk

        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0, stdout="exists")  # f1 skip

        pusher = FilePusher(runner)
        success, fail = pusher.push_files([f1, f2], "/sdcard/otter")

        assert success == 1
        assert fail == 1


class TestGetConnectorLazy:
    def test_creates_connector_when_none_injected(self, tmp_path):
        from unittest.mock import patch, MagicMock
        runner = FakeSubprocessRunner()
        pusher = FilePusher(runner)  # no connector injected
        mock_dc = MagicMock()
        with patch("cli.adb_connect.DeviceConnector", return_value=mock_dc):
            result = pusher._get_connector()
        assert result is mock_dc
