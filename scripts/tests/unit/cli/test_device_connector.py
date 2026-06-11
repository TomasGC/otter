#!/usr/bin/env python3
"""Unit tests for DeviceConnector."""

import json
import subprocess
import sys
from pathlib import Path

import pytest

from adb_connect import DeviceConnector
from fake_subprocess import FakeSubprocessRunner

ADB_DEVICES_CONNECTED = "List of devices attached\n192.168.1.10:5555\tdevice\n"
ADB_DEVICES_EMPTY = "List of devices attached\n"
MDNS_ONE_DEVICE = (
    "List of services\n"
    "adb-ABCD1234EFG-XyZ123\t_adb-tls-connect._tcp\t192.168.1.10:39007\n"
)
MDNS_TWO_DEVICES = (
    "List of services\n"
    "adb-ABCD1234EFG-XyZ123\t_adb-tls-connect._tcp\t192.168.1.10:39007\n"
    "adb-ZZZZ9999AAA-AbC456\t_adb-tls-connect._tcp\t192.168.1.20:40001\n"
)

def make_connector(runner, tmp_path, input_fn=None):
    config = tmp_path / ".device_cache.json"
    return DeviceConnector(runner, config, input_fn=input_fn or (lambda _: ""))

# ---------------------------------------------------------------------------
# get_connected
# ---------------------------------------------------------------------------

class TestGetConnected:
    def test_returns_empty_when_no_devices(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout=ADB_DEVICES_EMPTY)
        assert make_connector(runner, tmp_path).get_connected() == []

    def test_returns_ip_port_device(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout=ADB_DEVICES_CONNECTED)
        devices = make_connector(runner, tmp_path).get_connected()
        assert "192.168.1.10:5555" in devices

    def test_returns_empty_on_error(self, tmp_path):
        import subprocess
        runner = FakeSubprocessRunner()
        def raise_err(cmd, **kw):
            raise subprocess.CalledProcessError(1, cmd)
        runner.run = raise_err
        assert make_connector(runner, tmp_path).get_connected() == []

# ---------------------------------------------------------------------------
# discover
# ---------------------------------------------------------------------------

class TestDiscover:
    def test_returns_one_device(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout=MDNS_ONE_DEVICE)
        devices = make_connector(runner, tmp_path).discover()
        assert len(devices) == 1
        device_id, ip, port = devices[0]
        assert "ABCD1234EFG" in device_id
        assert ip == "192.168.1.10"
        assert port == "39007"

    def test_returns_multiple_devices(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout=MDNS_TWO_DEVICES)
        devices = make_connector(runner, tmp_path).discover()
        assert len(devices) == 2

    def test_returns_empty_when_no_mdns(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout="List of services\n")
        assert make_connector(runner, tmp_path).discover() == []

    def test_calls_adb_mdns_services(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout=MDNS_ONE_DEVICE)
        make_connector(runner, tmp_path).discover()
        assert runner.called_with("adb", "mdns", "services")

# ---------------------------------------------------------------------------
# pair / connect
# ---------------------------------------------------------------------------

class TestPair:
    def test_returns_true_on_success(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout="Successfully paired to 192.168.1.10:37445")
        assert make_connector(runner, tmp_path).pair("192.168.1.10", "37445", "123456") is True

    def test_returns_false_on_failure(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout="Failed to pair")
        assert make_connector(runner, tmp_path).pair("192.168.1.10", "37445", "000000") is False

    def test_calls_adb_pair(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout="Successfully paired")
        make_connector(runner, tmp_path).pair("192.168.1.10", "37445", "123456")
        assert runner.called_with("adb", "pair")

class TestConnect:
    def test_returns_true_when_connected(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout="connected to 192.168.1.10:39007")
        assert make_connector(runner, tmp_path).connect("192.168.1.10", "39007") is True

    def test_returns_true_when_already_connected(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout="already connected to 192.168.1.10:39007")
        assert make_connector(runner, tmp_path).connect("192.168.1.10", "39007") is True

    def test_returns_false_on_failure(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout="failed to connect")
        assert make_connector(runner, tmp_path).connect("192.168.1.10", "39007") is False

    def test_calls_adb_connect(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout="connected")
        make_connector(runner, tmp_path).connect("192.168.1.10", "39007")
        assert runner.called_with("adb", "connect")

# ---------------------------------------------------------------------------
# Device config (save / load / clear)
# ---------------------------------------------------------------------------

class TestDeviceConfig:
    def test_save_writes_json(self, tmp_path):
        runner = FakeSubprocessRunner()
        c = make_connector(runner, tmp_path)
        c.save("DEVICE_XYZ")
        config = tmp_path / ".device_cache.json"
        data = json.loads(config.read_text())
        assert data["device_id"] == "DEVICE_XYZ"

    def test_load_reads_saved_id(self, tmp_path):
        runner = FakeSubprocessRunner()
        c = make_connector(runner, tmp_path)
        c.save("DEVICE_ABC")
        assert c.load_saved() == "DEVICE_ABC"

    def test_load_returns_none_when_no_file(self, tmp_path):
        runner = FakeSubprocessRunner()
        c = make_connector(runner, tmp_path)
        assert c.load_saved() is None

    def test_clear_removes_file(self, tmp_path):
        runner = FakeSubprocessRunner()
        c = make_connector(runner, tmp_path)
        c.save("DEVICE_ABC")
        c.clear_saved()
        assert c.load_saved() is None

    def test_clear_is_idempotent_when_no_file(self, tmp_path):
        runner = FakeSubprocessRunner()
        make_connector(runner, tmp_path).clear_saved()  # must not raise

# ---------------------------------------------------------------------------
# auto_connect — already connected
# ---------------------------------------------------------------------------

class TestAutoConnectAlreadyConnected:
    def test_returns_existing_connection(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout=ADB_DEVICES_CONNECTED)
        result = make_connector(runner, tmp_path).auto_connect()
        assert result == "192.168.1.10:5555"

    def test_does_not_call_mdns_when_connected(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout=ADB_DEVICES_CONNECTED)
        make_connector(runner, tmp_path).auto_connect()
        assert not runner.called_with("mdns")

# ---------------------------------------------------------------------------
# auto_connect — discovery + connect
# ---------------------------------------------------------------------------

class TestAutoConnectDiscovery:
    def test_connects_to_single_discovered_device(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)      # get_connected → empty
            .add_run(stdout=MDNS_ONE_DEVICE)         # discover
            .add_run(stdout=ADB_DEVICES_EMPTY)       # is_device_connected → not yet
            .add_run(stdout="connected to 192.168.1.10:39007")  # connect
        )
        result = make_connector(runner, tmp_path).auto_connect()
        assert result == "192.168.1.10:39007"

    def test_returns_none_when_no_devices_discovered(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout="List of services\n")
        )
        result = make_connector(runner, tmp_path).auto_connect()
        assert result is None

    def test_uses_saved_device_when_available(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout=MDNS_ONE_DEVICE)
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout="connected to 192.168.1.10:39007")
        )
        c = make_connector(runner, tmp_path)
        c.save("ABCD1234EFG")
        result = c.auto_connect()
        assert result == "192.168.1.10:39007"

# ---------------------------------------------------------------------------
# Device config — exception paths
# ---------------------------------------------------------------------------

class TestDeviceConfigExceptions:
    def test_load_saved_returns_none_on_corrupt_json(self, tmp_path, monkeypatch):
        runner = FakeSubprocessRunner()
        c = make_connector(runner, tmp_path)
        config = tmp_path / ".device_cache.json"
        config.write_text("NOT_JSON")
        assert c.load_saved() is None

    def test_save_handles_oserror(self, tmp_path, monkeypatch):
        runner = FakeSubprocessRunner()
        c = make_connector(runner, tmp_path)
        monkeypatch.setattr(Path, "mkdir", lambda *a, **kw: (_ for _ in ()).throw(OSError("no space")))
        c.save("DEVICE_X")  # must not raise

    def test_clear_saved_handles_oserror(self, tmp_path, monkeypatch):
        runner = FakeSubprocessRunner()
        c = make_connector(runner, tmp_path)
        config = tmp_path / ".device_cache.json"
        config.write_text("{}")
        monkeypatch.setattr(Path, "unlink", lambda *a, **kw: (_ for _ in ()).throw(OSError("busy")))
        c.clear_saved()  # must not raise

# ---------------------------------------------------------------------------
# discover — exception path
# ---------------------------------------------------------------------------

class TestDiscoverException:
    def test_returns_empty_on_exception(self, tmp_path):
        runner = FakeSubprocessRunner()
        def raise_err(cmd, **kw):
            raise FileNotFoundError("adb not found")
        runner.run = raise_err
        assert make_connector(runner, tmp_path).discover() == []

    def test_skips_line_with_device_id_but_no_ip(self, tmp_path):
        mdns_no_ip = "List of services\nadb-ABCD1234EFG-XyZ123\t_adb-tls-connect._tcp\tno-ip-here\n"
        runner = FakeSubprocessRunner().add_run(stdout=mdns_no_ip)
        assert make_connector(runner, tmp_path).discover() == []

# ---------------------------------------------------------------------------
# is_device_connected
# ---------------------------------------------------------------------------

class TestIsDeviceConnected:
    def test_returns_true_when_found(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout=ADB_DEVICES_CONNECTED)
        ok, dev = make_connector(runner, tmp_path).is_device_connected("192.168.1.10:5555")
        assert ok is True
        assert dev == "192.168.1.10:5555"

    def test_returns_false_when_not_found(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout=ADB_DEVICES_EMPTY)
        ok, dev = make_connector(runner, tmp_path).is_device_connected("192.168.9.9:5555")
        assert ok is False
        assert dev is None

    def test_returns_false_on_exception(self, tmp_path):
        runner = FakeSubprocessRunner()
        runner.run = lambda *a, **kw: (_ for _ in ()).throw(FileNotFoundError("fail"))
        ok, dev = make_connector(runner, tmp_path).is_device_connected("any")
        assert ok is False

# ---------------------------------------------------------------------------
# pair / connect — exception paths
# ---------------------------------------------------------------------------

class TestPairException:
    def test_returns_false_on_exception(self, tmp_path):
        runner = FakeSubprocessRunner()
        runner.run = lambda *a, **kw: (_ for _ in ()).throw(subprocess.TimeoutExpired(["adb"], 5))
        assert make_connector(runner, tmp_path).pair("1.2.3.4", "9999", "000000") is False

class TestConnectException:
    def test_returns_false_on_exception(self, tmp_path):
        runner = FakeSubprocessRunner()
        runner.run = lambda *a, **kw: (_ for _ in ()).throw(subprocess.TimeoutExpired(["adb"], 5))
        assert make_connector(runner, tmp_path).connect("1.2.3.4", "9999") is False

# ---------------------------------------------------------------------------
# auto_connect — pairing credentials path
# ---------------------------------------------------------------------------

class TestAutoConnectWithCredentials:
    def test_calls_pair_and_connect(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)           # get_connected
            .add_run(stdout="Successfully paired")        # pair
            .add_run(stdout=MDNS_ONE_DEVICE)             # discover after pair
            .add_run(stdout="connected to 192.168.1.10:39007")  # connect
        )
        result = make_connector(runner, tmp_path).auto_connect(
            pairing_code="123456", pairing_address="192.168.1.10:45678"
        )
        assert result == "192.168.1.10:39007"

    def test_returns_none_when_pair_fails(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout="Failed to pair")
        )
        result = make_connector(runner, tmp_path).auto_connect(
            pairing_code="000000", pairing_address="192.168.1.10:45678"
        )
        assert result is None

# ---------------------------------------------------------------------------
# auto_connect — target_device filter
# ---------------------------------------------------------------------------

class TestAutoConnectTargetDevice:
    def test_matches_target_device(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout=MDNS_ONE_DEVICE)
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout="connected to 192.168.1.10:39007")
        )
        result = make_connector(runner, tmp_path).auto_connect(target_device="ABCD1234EFG")
        assert result == "192.168.1.10:39007"

    def test_returns_none_when_target_not_found(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout=MDNS_ONE_DEVICE)
        )
        result = make_connector(runner, tmp_path).auto_connect(target_device="UNKNOWN_DEVICE_ID")
        assert result is None

# ---------------------------------------------------------------------------
# auto_connect — saved exact match (lines 181-182)
# ---------------------------------------------------------------------------

class TestAutoConnectSavedExact:
    def test_uses_exact_saved_device_id(self, tmp_path):
        device_id = "adb-ABCD1234EFG-XyZ123"
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout=MDNS_ONE_DEVICE)
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout="connected to 192.168.1.10:39007")
        )
        c = make_connector(runner, tmp_path)
        c.save(device_id)
        result = c.auto_connect()
        assert result == "192.168.1.10:39007"

# ---------------------------------------------------------------------------
# auto_connect — multiple devices (lines 194-198)
# ---------------------------------------------------------------------------

class TestAutoConnectMultipleDevices:
    def test_choose_device_selects_first(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout=MDNS_TWO_DEVICES)
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout="connected to 192.168.1.10:39007")
        )
        c = make_connector(runner, tmp_path, input_fn=lambda _: "1")
        result = c.auto_connect()
        assert result == "192.168.1.10:39007"

    def test_choose_device_quit_returns_none(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout=MDNS_TWO_DEVICES)
        )
        c = make_connector(runner, tmp_path, input_fn=lambda _: "q")
        assert c.auto_connect() is None

# ---------------------------------------------------------------------------
# auto_connect — already connected after selection (lines 203-204)
# ---------------------------------------------------------------------------

class TestAutoConnectAlreadyConnectedAfterSelect:
    def test_returns_existing_when_already_connected_after_select(self, tmp_path):
        # Device is found via mDNS; is_device_connected finds it already in adb devices
        adb_with_mdns_id = "List of devices attached\nadb-ABCD1234EFG-XyZ123\tdevice\n"
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)         # get_connected → none
            .add_run(stdout=MDNS_ONE_DEVICE)            # discover
            .add_run(stdout=adb_with_mdns_id)           # is_device_connected → True
        )
        result = make_connector(runner, tmp_path).auto_connect()
        assert result == "adb-ABCD1234EFG-XyZ123"

# ---------------------------------------------------------------------------
# auto_connect — connect fails → interactive pair (line 214)
# ---------------------------------------------------------------------------

class TestAutoConnectInteractiveFallback:
    def test_falls_through_to_interactive_pair_on_connect_fail(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout=MDNS_ONE_DEVICE)
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout="failed to connect")        # connect fails
            .add_run(stdout="Successfully paired")      # pair in interactive
            .add_run(stdout=MDNS_ONE_DEVICE)            # discover after pair
            .add_run(stdout="connected to 192.168.1.10:39007")  # connect after pair
        )
        responses = iter(["111111", "192.168.1.10:45678"])
        c = make_connector(runner, tmp_path, input_fn=lambda _: next(responses))
        result = c.auto_connect()
        assert result == "192.168.1.10:39007"

    def test_interactive_pair_returns_none_on_eof(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout=MDNS_ONE_DEVICE)
            .add_run(stdout=ADB_DEVICES_EMPTY)
            .add_run(stdout="failed to connect")
        )
        c = make_connector(runner, tmp_path, input_fn=lambda _: (_ for _ in ()).throw(EOFError()))
        assert c.auto_connect() is None

# ---------------------------------------------------------------------------
# _pair_and_connect
# ---------------------------------------------------------------------------

class TestPairAndConnect:
    def _make(self, runner, tmp_path):
        config = tmp_path / ".device_cache.json"
        return DeviceConnector(runner, config)

    def test_returns_connection_on_success(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout="Successfully paired")
            .add_run(stdout=MDNS_ONE_DEVICE)
            .add_run(stdout="connected to 192.168.1.10:39007")
        )
        result = self._make(runner, tmp_path)._pair_and_connect(
            "192.168.1.10:45678", "123456"
        )
        assert result == "192.168.1.10:39007"

    def test_returns_none_when_pair_fails(self, tmp_path):
        runner = FakeSubprocessRunner().add_run(stdout="Failed to pair")
        assert self._make(runner, tmp_path)._pair_and_connect("1.2.3.4:9999", "000000") is None

    def test_returns_none_when_no_matching_device_after_pair(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout="Successfully paired")
            .add_run(stdout="List of services\n")  # discover returns empty
        )
        assert self._make(runner, tmp_path)._pair_and_connect("1.2.3.4:9999", "111111") is None

    def test_returns_none_when_connect_fails_after_pair(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout="Successfully paired")
            .add_run(stdout=MDNS_ONE_DEVICE)
            .add_run(stdout="failed to connect")
        )
        assert self._make(runner, tmp_path)._pair_and_connect("192.168.1.10:45678", "123456") is None

# ---------------------------------------------------------------------------
# _interactive_pair
# ---------------------------------------------------------------------------

class TestInteractivePair:
    def _make(self, runner, tmp_path, input_fn):
        config = tmp_path / ".device_cache.json"
        return DeviceConnector(runner, config, input_fn=input_fn)

    def test_returns_connection_on_success(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout="Successfully paired")
            .add_run(stdout=MDNS_ONE_DEVICE)
            .add_run(stdout="connected to 192.168.1.10:39007")
        )
        responses = iter(["111111", "192.168.1.10:45678"])
        c = self._make(runner, tmp_path, input_fn=lambda _: next(responses))
        assert c._interactive_pair("192.168.1.10", "39007") == "192.168.1.10:39007"

    def test_returns_none_on_invalid_address_format(self, tmp_path):
        runner = FakeSubprocessRunner()
        responses = iter(["111111", "NO_COLON_HERE"])
        c = self._make(runner, tmp_path, input_fn=lambda _: next(responses))
        assert c._interactive_pair("1.2.3.4", "9999") is None

    def test_returns_none_on_pair_failure(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout="Failed to pair")
        )
        responses = iter(["000000", "1.2.3.4:5555"])
        c = self._make(runner, tmp_path, input_fn=lambda _: next(responses))
        assert c._interactive_pair("1.2.3.4", "9999") is None

    def test_returns_none_when_no_device_after_pair(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout="Successfully paired")
            .add_run(stdout="List of services\n")  # empty discover
        )
        responses = iter(["111111", "1.2.3.4:5555"])
        c = self._make(runner, tmp_path, input_fn=lambda _: next(responses))
        assert c._interactive_pair("1.2.3.4", "9999") is None

    def test_returns_none_when_connect_fails_after_pair(self, tmp_path):
        runner = (
            FakeSubprocessRunner()
            .add_run(stdout="Successfully paired")
            .add_run(stdout=MDNS_ONE_DEVICE)
            .add_run(stdout="failed to connect")
        )
        responses = iter(["111111", "192.168.1.10:5555"])
        c = self._make(runner, tmp_path, input_fn=lambda _: next(responses))
        assert c._interactive_pair("192.168.1.10", "39007") is None

    def test_returns_none_on_keyboard_interrupt(self, tmp_path):
        runner = FakeSubprocessRunner()
        c = self._make(runner, tmp_path, input_fn=lambda _: (_ for _ in ()).throw(KeyboardInterrupt()))
        assert c._interactive_pair("1.2.3.4", "9999") is None

# ---------------------------------------------------------------------------
# _choose_device
# ---------------------------------------------------------------------------

class TestChooseDevice:
    def _make(self, runner, tmp_path, input_fn):
        config = tmp_path / ".device_cache.json"
        return DeviceConnector(runner, config, input_fn=input_fn)

    def test_returns_selected_device(self, tmp_path):
        runner = FakeSubprocessRunner()
        devices = [("dev1", "1.1.1.1", "5555"), ("dev2", "2.2.2.2", "6666")]
        c = self._make(runner, tmp_path, input_fn=lambda _: "2")
        assert c._choose_device(devices) == ("dev2", "2.2.2.2", "6666")

    def test_returns_none_on_quit(self, tmp_path):
        runner = FakeSubprocessRunner()
        c = self._make(runner, tmp_path, input_fn=lambda _: "q")
        assert c._choose_device([("dev1", "1.1.1.1", "5555")]) is None

    def test_returns_none_on_out_of_range(self, tmp_path):
        runner = FakeSubprocessRunner()
        c = self._make(runner, tmp_path, input_fn=lambda _: "99")
        assert c._choose_device([("dev1", "1.1.1.1", "5555")]) is None

    def test_returns_none_on_value_error(self, tmp_path):
        runner = FakeSubprocessRunner()
        c = self._make(runner, tmp_path, input_fn=lambda _: "notanumber")
        assert c._choose_device([("dev1", "1.1.1.1", "5555")]) is None
