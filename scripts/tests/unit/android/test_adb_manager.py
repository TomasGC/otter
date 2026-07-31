#!/usr/bin/env python3
"""Unit and integration tests for AdbManager."""

import subprocess
from unittest.mock import patch

from adb_fixtures import (
    ADB_DEVICES_EMPTY,
    ADB_DEVICES_EMULATOR,
    ADB_DEVICES_EMULATOR_OFFLINE,
    ADB_DEVICES_MDNS,
    ADB_DEVICES_MULTI,
    ADB_DEVICES_ONE,
    AVD_LIST_MULTI,
    AVD_LIST_ONE,
)
from fake_subprocess import FakeSubprocessRunner

from android.adb import AdbManager

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
            FakeSubprocessRunner().add_run(returncode=0).add_run(returncode=0, stdout=ADB_DEVICES_ONE)  # is_available
        )
        assert AdbManager(runner).get_connected() == ["ABCD1234"]

    def test_returns_multiple_devices(self):
        runner = FakeSubprocessRunner().add_run(returncode=0).add_run(returncode=0, stdout=ADB_DEVICES_MULTI)
        devices = AdbManager(runner).get_connected()
        assert "ABCD1234" in devices
        assert "EF567890" in devices

    def test_returns_empty_when_no_devices_attached(self):
        runner = FakeSubprocessRunner().add_run(returncode=0).add_run(returncode=0, stdout=ADB_DEVICES_EMPTY)
        assert AdbManager(runner).get_connected() == []

    def test_parses_mdns_device_id(self):
        runner = FakeSubprocessRunner().add_run(returncode=0).add_run(returncode=0, stdout=ADB_DEVICES_MDNS)
        devices = AdbManager(runner).get_connected()
        assert len(devices) == 1
        assert "ABCD1234EFG" in devices[0]

    def test_calls_adb_devices(self):
        runner = FakeSubprocessRunner().add_run(returncode=0).add_run(returncode=0, stdout=ADB_DEVICES_ONE)
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


# ---------------------------------------------------------------------------
# get_running_emulators
# ---------------------------------------------------------------------------


class TestGetRunningEmulators:
    def test_returns_empty_when_adb_unavailable(self):
        runner = FakeSubprocessRunner().add_run(returncode=1)
        assert AdbManager(runner).get_running_emulators() == []

    def test_returns_emulator_id_when_present(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)
            .add_run(returncode=0, stdout=ADB_DEVICES_EMULATOR)
        )
        result = AdbManager(runner).get_running_emulators()
        assert result == ["emulator-5554"]

    def test_returns_emulator_even_when_offline(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)
            .add_run(returncode=0, stdout=ADB_DEVICES_EMULATOR_OFFLINE)
        )
        result = AdbManager(runner).get_running_emulators()
        assert result == ["emulator-5554"]

    def test_excludes_physical_devices(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)
            .add_run(returncode=0, stdout=ADB_DEVICES_ONE)
        )
        assert AdbManager(runner).get_running_emulators() == []

    def test_returns_empty_when_no_devices(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)
            .add_run(returncode=0, stdout=ADB_DEVICES_EMPTY)
        )
        assert AdbManager(runner).get_running_emulators() == []

    def test_returns_empty_on_subprocess_error(self):
        runner = FakeSubprocessRunner().add_run(returncode=0)

        def raise_err(cmd, **kw):
            runner.calls.append(list(cmd))
            if "devices" in cmd:
                raise subprocess.CalledProcessError(1, cmd)
            return FakeSubprocessRunner().add_run().run(cmd, **kw)

        runner.run = raise_err
        assert AdbManager(runner).get_running_emulators() == []


# ---------------------------------------------------------------------------
# list_avds
# ---------------------------------------------------------------------------


class TestListAvds:
    def test_returns_avd_names(self):
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout=AVD_LIST_ONE)
        with patch("shutil.which", return_value="emulator"):
            result = AdbManager(runner).list_avds()
        assert result == ["Pixel_6_API_34"]

    def test_returns_multiple_avds(self):
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout=AVD_LIST_MULTI)
        with patch("shutil.which", return_value="emulator"):
            result = AdbManager(runner).list_avds()
        assert result == ["Pixel_6_API_34", "Pixel_8_API_35"]

    def test_returns_empty_when_no_avds(self):
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout="")
        with patch("shutil.which", return_value="emulator"):
            assert AdbManager(runner).list_avds() == []

    def test_returns_empty_when_emulator_not_found(self):
        runner = FakeSubprocessRunner()

        def raise_fnf(cmd, **kw):
            runner.calls.append(list(cmd))
            raise FileNotFoundError("emulator not found")

        runner.run = raise_fnf
        with patch("shutil.which", return_value="emulator"):
            assert AdbManager(runner).list_avds() == []

    def test_calls_emulator_list_avds(self):
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout=AVD_LIST_ONE)
        with patch("shutil.which", return_value="emulator"):
            AdbManager(runner).list_avds()
        assert runner.called_with("emulator", "-list-avds")

    def test_uses_android_home_when_not_in_path(self):
        runner = FakeSubprocessRunner().add_run(returncode=0, stdout=AVD_LIST_ONE)
        with patch("shutil.which", return_value=None), \
             patch.dict("os.environ", {"ANDROID_HOME": "/fake/sdk"}, clear=True), \
             patch("pathlib.Path.exists", return_value=True):
            result = AdbManager(runner).list_avds()
        assert result == ["Pixel_6_API_34"]
        assert runner.called_with("-list-avds")

    def test_returns_empty_when_no_binary_anywhere(self):
        runner = FakeSubprocessRunner()
        with patch("shutil.which", return_value=None), \
             patch.dict("os.environ", {}, clear=True):
            result = AdbManager(runner).list_avds()
        assert result == []
        assert runner.call_count == 0


# ---------------------------------------------------------------------------
# wait_for_emulator
# ---------------------------------------------------------------------------


class TestWaitForEmulator:
    def test_returns_device_id_when_ready(self):
        # adb -e wait-for-device → adb version (is_available) → adb devices → shell boot loop
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)                                # adb -e wait-for-device
            .add_run(returncode=0)                                # adb version (is_available)
            .add_run(returncode=0, stdout=ADB_DEVICES_EMULATOR)  # adb devices
            .add_run(returncode=0)                                # shell boot loop exits cleanly
        )
        result = AdbManager(runner).wait_for_emulator(timeout=30)
        assert result == "emulator-5554"

    def test_returns_none_when_no_emulators_after_wait(self):
        # wait-for-device succeeds but adb devices shows nothing (race: offline emulator evicted)
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)                               # adb -e wait-for-device
            .add_run(returncode=0)                               # adb version
            .add_run(returncode=0, stdout=ADB_DEVICES_EMPTY)    # adb devices → empty
        )
        result = AdbManager(runner).wait_for_emulator(timeout=30)
        assert result is None

    def test_returns_none_when_boot_loop_times_out(self):
        # Device connected but sys.boot_completed never reaches 1 → shell exits non-zero
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)                                # adb -e wait-for-device
            .add_run(returncode=0)                                # adb version
            .add_run(returncode=0, stdout=ADB_DEVICES_EMULATOR)  # adb devices
            .add_run(returncode=1)                                # shell boot loop fails/killed
        )
        result = AdbManager(runner).wait_for_emulator(timeout=30)
        assert result is None

    def test_returns_none_on_timeout_expired(self):
        import subprocess as _sp
        runner = FakeSubprocessRunner()

        def raise_timeout(cmd, **kw):
            runner.calls.append(list(cmd))
            raise _sp.TimeoutExpired(cmd, 30)

        runner.run = raise_timeout
        result = AdbManager(runner).wait_for_emulator(timeout=30)
        assert result is None

    def test_wait_for_device_called_before_boot_check(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)
            .add_run(returncode=0)
            .add_run(returncode=0, stdout=ADB_DEVICES_EMULATOR)
            .add_run(returncode=0)
        )
        AdbManager(runner).wait_for_emulator(timeout=30)
        assert runner.calls[0] == ["adb", "-e", "wait-for-device"]

    def test_boot_loop_targets_correct_emulator(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)
            .add_run(returncode=0)
            .add_run(returncode=0, stdout=ADB_DEVICES_EMULATOR)
            .add_run(returncode=0)
        )
        AdbManager(runner).wait_for_emulator(timeout=30)
        boot_cmd = runner.calls[3]
        assert boot_cmd[:3] == ["adb", "-s", "emulator-5554"]

    def test_boot_loop_uses_getprop_w(self):
        runner = (
            FakeSubprocessRunner()
            .add_run(returncode=0)
            .add_run(returncode=0)
            .add_run(returncode=0, stdout=ADB_DEVICES_EMULATOR)
            .add_run(returncode=0)
        )
        AdbManager(runner).wait_for_emulator(timeout=30)
        shell_script = " ".join(runner.calls[3])
        assert "getprop -w" in shell_script
        assert "sys.boot_completed" in shell_script


# ---------------------------------------------------------------------------
# start_emulator
# ---------------------------------------------------------------------------


class TestStartEmulator:
    def test_returns_true_on_success(self):
        runner = FakeSubprocessRunner()
        with patch("shutil.which", return_value="emulator"), \
             patch("subprocess.Popen"):
            result = AdbManager(runner).start_emulator("Pixel_6_API_34")
        assert result is True

    def test_returns_false_when_no_binary(self):
        runner = FakeSubprocessRunner()
        with patch("shutil.which", return_value=None), \
             patch.dict("os.environ", {}, clear=True):
            result = AdbManager(runner).start_emulator("Pixel_6_API_34")
        assert result is False

    def test_returns_false_on_file_not_found(self):
        runner = FakeSubprocessRunner()
        with patch("shutil.which", return_value="emulator"), \
             patch("subprocess.Popen", side_effect=FileNotFoundError()):
            result = AdbManager(runner).start_emulator("Pixel_6_API_34")
        assert result is False

    def test_calls_popen_with_correct_avd(self):
        runner = FakeSubprocessRunner()
        with patch("shutil.which", return_value="emulator"), \
             patch("subprocess.Popen") as mock_popen:
            AdbManager(runner).start_emulator("MyAVD")
        args = mock_popen.call_args[0][0]
        assert args == ["emulator", "-avd", "MyAVD"]

    def test_calls_popen_with_devnull_streams(self):
        import subprocess as _sp
        runner = FakeSubprocessRunner()
        with patch("shutil.which", return_value="emulator"), \
             patch("subprocess.Popen") as mock_popen:
            AdbManager(runner).start_emulator("TestAVD")
        kwargs = mock_popen.call_args[1]
        assert kwargs.get("stdout") == _sp.DEVNULL
        assert kwargs.get("stderr") == _sp.DEVNULL

    def test_does_not_call_runner(self):
        # Popen is fire-and-forget; injected runner must not be used.
        runner = FakeSubprocessRunner()
        with patch("shutil.which", return_value="emulator"), \
             patch("subprocess.Popen"):
            AdbManager(runner).start_emulator("TestAVD")
        assert runner.call_count == 0
