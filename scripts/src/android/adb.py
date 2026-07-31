#!/usr/bin/env python3
"""ADB device management utilities."""

import subprocess
from pathlib import Path

from common.constants import (
    TIMEOUT_ADB_DEVICES,
    TIMEOUT_ADB_VERSION,
    TIMEOUT_APK_INSTALL,
)
from common.subprocess_runner import SubprocessRunner


class AdbManager:
    """Manages ADB device operations via injected subprocess runner."""

    def __init__(self, runner: SubprocessRunner) -> None:
        self._runner = runner

    def is_available(self) -> bool:
        try:
            self._runner.run(
                ["adb", "version"],
                capture_output=True,
                timeout=TIMEOUT_ADB_VERSION,
                check=True,
            )
            return True
        except (subprocess.CalledProcessError, FileNotFoundError, subprocess.TimeoutExpired):
            return False

    def get_connected(self) -> list[str]:
        if not self.is_available():
            return []
        try:
            result = self._runner.run(
                ["adb", "devices"],
                capture_output=True,
                text=True,
                timeout=TIMEOUT_ADB_DEVICES,
                check=True,
            )
            devices = []
            for line in result.stdout.split("\n"):
                if "device" in line and "List of devices" not in line:
                    parts = line.split("\t")
                    if parts:
                        device_id = parts[0].strip()
                        if device_id:
                            devices.append(device_id)
            return devices
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            return []

    def get_running_emulators(self) -> list[str]:
        """Return emulator-* device IDs from adb devices (any status)."""
        if not self.is_available():
            return []
        try:
            result = self._runner.run(
                ["adb", "devices"],
                capture_output=True,
                text=True,
                timeout=TIMEOUT_ADB_DEVICES,
                check=True,
            )
            return [
                line.split("\t")[0].strip()
                for line in result.stdout.split("\n")
                if line.startswith("emulator-")
            ]
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            return []

    def _emulator_binary(self) -> str | None:
        import os
        import shutil
        import sys
        if shutil.which("emulator"):
            return "emulator"
        android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if android_home:
            suffix = ".exe" if sys.platform == "win32" else ""
            candidate = Path(android_home) / "emulator" / f"emulator{suffix}"
            if candidate.exists():
                return str(candidate)
        return None

    def list_avds(self) -> list[str]:
        binary = self._emulator_binary()
        if not binary:
            print("emulator binary not found — add Android SDK emulator to PATH or set ANDROID_HOME")
            return []
        try:
            result = self._runner.run(
                [binary, "-list-avds"],
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            return [line.strip() for line in result.stdout.splitlines() if line.strip()]
        except FileNotFoundError:
            return []

    def start_emulator(self, avd_name: str) -> bool:
        import subprocess as _sp
        binary = self._emulator_binary()
        if not binary:
            print("emulator binary not found — add Android SDK emulator to PATH or set ANDROID_HOME")
            return False
        try:
            _sp.Popen(
                [binary, "-avd", avd_name],
                stdout=_sp.DEVNULL,
                stderr=_sp.DEVNULL,
            )
            return True
        except FileNotFoundError:
            print("emulator binary not found")
            return False

    def wait_for_emulator(self, timeout: int = 180) -> str | None:
        import time
        print(f"Waiting for emulator to be ready (up to {timeout}s)...")
        deadline = time.time() + timeout
        try:
            # Block until any emulator reaches ADB device state — ADB handles waiting internally
            remaining = max(1, int(deadline - time.time()))
            self._runner.run(["adb", "-e", "wait-for-device"], timeout=remaining, check=False)

            emulators = self.get_running_emulators()
            if not emulators:
                return None
            emu = emulators[0]

            # Block until system fully booted — getprop -w suspends until property changes, no polling
            remaining = max(1, int(deadline - time.time()))
            result = self._runner.run(
                ["adb", "-s", emu, "shell",
                 "while [ \"$(getprop sys.boot_completed)\" != \"1\" ]; do getprop -w sys.boot_completed; done"],
                timeout=remaining,
                check=False,
            )
            return emu if result.returncode == 0 else None
        except subprocess.TimeoutExpired:
            return None

    def install_apk(self, apk_path: Path, device: str | None = None) -> bool:
        if not apk_path.exists():
            print(f"APK not found: {apk_path}")
            return False
        cmd = ["adb"]
        if device:
            cmd.extend(["-s", device])
        cmd.extend(["install", "-r", str(apk_path)])
        try:
            result = self._runner.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=TIMEOUT_APK_INSTALL,
                check=True,
            )
            if "Success" in result.stdout:
                return True
            print(f"Install failed: {result.stdout}")
            return False
        except subprocess.CalledProcessError as e:
            print(f"Install failed: {e.stderr}")
            return False
        except subprocess.TimeoutExpired:
            print("Install timed out")
            return False
