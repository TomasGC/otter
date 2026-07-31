#!/usr/bin/env python3
"""E2E — wait_for_emulator against a live emulator.

Auto-skipped by conftest when no ADB device/emulator is connected.
Additionally skips per-test when no emulator specifically is running
(device connected but it's a physical phone, not an emulator).
"""

import pytest

from android.adb import AdbManager
from common.subprocess_runner import RealSubprocessRunner

pytestmark = [pytest.mark.e2e, pytest.mark.local_only]


class TestEmulatorBootE2e:
    def _adb(self) -> AdbManager:
        return AdbManager(RealSubprocessRunner())

    def test_wait_for_emulator_returns_id(self):
        adb = self._adb()
        if not adb.get_running_emulators():
            pytest.skip("no running emulator — start one to exercise this path")
        result = adb.wait_for_emulator(timeout=60)
        assert result is not None
        assert result.startswith("emulator-")

    def test_returned_id_matches_running_emulator(self):
        adb = self._adb()
        emulators = adb.get_running_emulators()
        if not emulators:
            pytest.skip("no running emulator")
        assert adb.wait_for_emulator(timeout=60) in emulators
