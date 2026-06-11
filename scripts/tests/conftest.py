"""Root conftest — adds src, src/cli, and test helpers to sys.path for all tests."""

import sys
from pathlib import Path

_SCRIPTS = Path(__file__).parent.parent
sys.path.insert(0, str(_SCRIPTS / "src"))
sys.path.insert(0, str(_SCRIPTS / "src" / "cli"))
sys.path.insert(0, str(Path(__file__).parent / "helpers"))
