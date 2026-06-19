#!/usr/bin/env python3
"""Integration tests for AdbManager — real filesystem, fake subprocess."""

import pytest
from fake_subprocess import FakeSubprocessRunner

from android.adb import AdbManager

pytestmark = pytest.mark.integration_mock


class TestInstallApkIntegration:
    """install_apk with real tmp_path APK files."""

    def test_install_real_apk_file_succeeds(self, tmp_path):
        apk = tmp_path / "app-debug.apk"
        apk.write_bytes(b"PK\x03\x04FAKE_APK_CONTENT")
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout="Success\n")
        assert AdbManager(runner).install_apk(apk) is True

    def test_install_apk_passes_correct_path(self, tmp_path):
        apk = tmp_path / "app-debug.apk"
        apk.write_bytes(b"FAKE")
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout="Success\n")
        AdbManager(runner).install_apk(apk)
        assert str(apk) in " ".join(runner.last_call())

    def test_install_apk_with_device_flag(self, tmp_path):
        apk = tmp_path / "app.apk"
        apk.write_bytes(b"FAKE")
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout="Success\n")
        AdbManager(runner).install_apk(apk, device="192.168.1.1:5555")
        assert runner.called_with("-s", "192.168.1.1:5555")
        assert str(apk) in " ".join(runner.last_call())

    def test_missing_apk_does_not_call_subprocess(self, tmp_path):
        runner = FakeSubprocessRunner()
        AdbManager(runner).install_apk(tmp_path / "nonexistent.apk")
        assert runner.call_count == 0

    def test_install_returns_false_for_empty_apk(self, tmp_path):
        # Zero-byte file exists but install reports failure
        apk = tmp_path / "empty.apk"
        apk.write_bytes(b"")
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout="Failure [CORRUPT]\n")
        assert AdbManager(runner).install_apk(apk) is False


class TestGetDevicesIntegration:
    """get_devices parses real-looking adb output."""

    ADB_REAL_OUTPUT = (
        "List of devices attached\n"
        "adb-ABCD1234EFG-XyZ123 (2)._adb-tls-connect._tcp.\tdevice\n"
        "* daemon started successfully\n"
    )

    def test_parses_mdns_device_full_line(self):
        runner = FakeSubprocessRunner().add_run(returncode=0).add_run(returncode=0, stdout=self.ADB_REAL_OUTPUT)
        devices = AdbManager(runner).get_connected()
        assert len(devices) == 1
        assert "ABCD1234EFG" in devices[0]

    def test_daemon_line_not_included(self):
        runner = FakeSubprocessRunner().add_run(returncode=0).add_run(returncode=0, stdout=self.ADB_REAL_OUTPUT)
        devices = AdbManager(runner).get_connected()
        assert not any("daemon" in d for d in devices)
