import shutil
import uuid
from pathlib import Path

import pytest

_TEMP_ROOT = Path(__file__).parents[3] / "temp" / "docker-tests"


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
