import subprocess
import pytest


def _has_adb_device() -> bool:
    try:
        result = subprocess.run(
            ["adb", "devices"],
            capture_output=True, text=True, timeout=5
        )
        devices = [
            line for line in result.stdout.splitlines()[1:]
            if line.strip() and "\tdevice" in line
        ]
        return len(devices) > 0
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return False


def pytest_collection_modifyitems(items):
    if not _has_adb_device():
        skip = pytest.mark.skip(reason="E2E tests require a connected ADB device or emulator")
        for item in items:
            if item.get_closest_marker("e2e"):
                item.add_marker(skip)
