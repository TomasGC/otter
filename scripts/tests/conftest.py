"""Root conftest — adds src, src/cli, test helpers, and scripts root to sys.path."""

import os
import sys
from pathlib import Path

import pytest

_SCRIPTS = Path(__file__).parent.parent
sys.path.insert(0, str(_SCRIPTS))  # for pipeline.py at scripts/ root
sys.path.insert(0, str(_SCRIPTS / "src"))
sys.path.insert(0, str(_SCRIPTS / "src" / "cli"))
sys.path.insert(0, str(Path(__file__).parent / "helpers"))


def pytest_collection_modifyitems(items):
    if os.environ.get("CI"):
        skip = pytest.mark.skip(reason="local_only — requires dev setup not available in CI")
        for item in items:
            if item.get_closest_marker("local_only"):
                item.add_marker(skip)
