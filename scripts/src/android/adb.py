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
