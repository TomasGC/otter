import shutil
import subprocess
import uuid
from pathlib import Path

import pytest

_TEMP_ROOT = Path(__file__).parents[2] / "temp" / "docker-tests"


@pytest.fixture
def docker_tmp():
    """Workspace under repo temp/ instead of pytest tmp_path.

    pytest creates tmp_path with private ACLs on Windows, which the Docker
    daemon cannot mount as a volume ("Access is denied").
    """
    workspace = _TEMP_ROOT / uuid.uuid4().hex
    workspace.mkdir(parents=True)
    yield workspace
    shutil.rmtree(workspace, ignore_errors=True)


def _has_adb_device() -> bool:
    try:
        result = subprocess.run(["adb", "devices"], capture_output=True, text=True, timeout=5)
        devices = [line for line in result.stdout.splitlines()[1:] if line.strip() and "\tdevice" in line]
        return len(devices) > 0
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return False


def pytest_collection_modifyitems(items):
    if not _has_adb_device():
        skip = pytest.mark.skip(reason="E2E tests require a connected ADB device or emulator")
        for item in items:
            if item.get_closest_marker("e2e"):
                item.add_marker(skip)
