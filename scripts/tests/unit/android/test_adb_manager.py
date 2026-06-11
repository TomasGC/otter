#!/usr/bin/env python3
"""Unit and integration tests for AdbManager."""

import subprocess

import pytest

from android.adb import AdbManager
from fake_subprocess import FakeSubprocessRunner
from adb_fixtures import ADB_DEVICES_ONE, ADB_DEVICES_MULTI, ADB_DEVICES_EMPTY, ADB_DEVICES_MDNS


# ---------------------------------------------------------------------------
# is_available
# ---------------------------------------------------------------------------

class TestIsAvailable:
    def test_returns_true_when_adb_responds(self):
        runner = FakeSubprocessRunner().add_run(returncode=0)
        assert AdbManager(runner).is_available() is True

    def test_returns_false_when_adb_fails(self):
        runner = FakeSubprocessRunner().add_run(returncode=1)
        assert AdbManager(runner).is_available() is False

    def test_returns_false_when_adb_not_found(self):
        runner = FakeSubprocessRunner()
        runner._run_queue = []
        # Simulate FileNotFoundError by having run() raise it
        def raise_fnf(cmd, **kw):
            runner.calls.append(list(cmd))
            raise FileNotFoundError("adb not found")
        runner.run = raise_fnf
        assert AdbManager(runner).is_available() is False

    def test_returns_false_on_timeout(self):
        runner = FakeSubprocessRunner()
        def raise_timeout(cmd, **kw):
            runner.calls.append(list(cmd))
            raise subprocess.TimeoutExpired(cmd, 3)
        runner.run = raise_timeout
        assert AdbManager(runner).is_available() is False

    def test_calls_adb_version(self):
        runner = FakeSubprocessRunner().add_run()
        AdbManager(runner).is_available()
        assert runner.called_with("adb", "version")


# ---------------------------------------------------------------------------
# get_connected
# ---------------------------------------------------------------------------

class TestGetConnected:
    def test_returns_empty_when_adb_unavailable(self):
        runner = FakeSubprocessRunner().add_run(returncode=1)
        assert AdbManager(runner).get_connected() == []

    def test_returns_single_device(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)          # is_available
            .add_run(returncode=0, stdout=ADB_DEVICES_ONE)
        )
        assert AdbManager(runner).get_connected() == ["ABCD1234"]

    def test_returns_multiple_devices(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)
            .add_run(returncode=0, stdout=ADB_DEVICES_MULTI)
        )
        devices = AdbManager(runner).get_connected()
        assert "ABCD1234" in devices
        assert "EF567890" in devices

    def test_returns_empty_when_no_devices_attached(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)
            .add_run(returncode=0, stdout=ADB_DEVICES_EMPTY)
        )
        assert AdbManager(runner).get_connected() == []

    def test_parses_mdns_device_id(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)
            .add_run(returncode=0, stdout=ADB_DEVICES_MDNS)
        )
        devices = AdbManager(runner).get_connected()
        assert len(devices) == 1
        assert "ABCD1234EFG" in devices[0]

    def test_calls_adb_devices(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)
            .add_run(returncode=0, stdout=ADB_DEVICES_ONE)
        )
        AdbManager(runner).get_connected()
        assert runner.called_with("adb", "devices")

    def test_returns_empty_on_subprocess_error(self):
        runner = FakeSubprocessRunner().add_run(returncode=0)
        def raise_err(cmd, **kw):
            runner.calls.append(list(cmd))
            if "devices" in cmd:
                raise subprocess.CalledProcessError(1, cmd)
            return FakeSubprocessRunner().add_run().run(cmd, **kw)
        runner.run = raise_err
        assert AdbManager(runner).get_connected() == []


# ---------------------------------------------------------------------------
# install_apk
# ---------------------------------------------------------------------------

class TestInstallApk:
    def test_returns_false_when_apk_missing(self, tmp_path):
        runner = FakeSubprocessRunner()
        result = AdbManager(runner).install_apk(tmp_path / "missing.apk")
        assert result is False
        assert runner.call_count == 0

    def test_returns_true_on_success(self, tmp_path):
        apk = tmp_path / "app.apk"
        apk.write_bytes(b"FAKE")
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout="Success\n")
        assert AdbManager(runner).install_apk(apk) is True

    def test_returns_false_when_output_lacks_success(self, tmp_path):
        apk = tmp_path / "app.apk"
        apk.write_bytes(b"FAKE")
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout="Failure [INSTALL_FAILED]\n")
        assert AdbManager(runner).install_apk(apk) is False

    def test_returns_false_on_subprocess_error(self, tmp_path):
        apk = tmp_path / "app.apk"
        apk.write_bytes(b"FAKE")
        runner = FakeSubprocessRunner().add_run(returncode=1, stderr="error")
        assert AdbManager(runner).install_apk(apk) is False

    def test_uses_device_flag_when_specified(self, tmp_path):
        apk = tmp_path / "app.apk"
        apk.write_bytes(b"FAKE")
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout="Success\n")
        AdbManager(runner).install_apk(apk, device="DEV123")
        assert runner.called_with("-s", "DEV123")

    def test_no_device_flag_when_omitted(self, tmp_path):
        apk = tmp_path / "app.apk"
        apk.write_bytes(b"FAKE")
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout="Success\n")
        AdbManager(runner).install_apk(apk)
        assert not runner.called_with("-s")

    def test_calls_adb_install(self, tmp_path):
        apk = tmp_path / "app.apk"
        apk.write_bytes(b"FAKE")
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout="Success\n")
        AdbManager(runner).install_apk(apk)
        assert runner.called_with("adb", "install", "-r")

    def test_returns_false_on_timeout(self, tmp_path):
        apk = tmp_path / "app.apk"
        apk.write_bytes(b"FAKE")
        runner = FakeSubprocessRunner()
        def raise_timeout(cmd, **kw):
            runner.calls.append(list(cmd))
            raise subprocess.TimeoutExpired(cmd, 60)
        runner.run = raise_timeout
        assert AdbManager(runner).install_apk(apk) is False
