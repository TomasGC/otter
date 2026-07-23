"""Root conftest — adds src, src/cli, test helpers, and scripts root to sys.path."""

import sys
from pathlib import Path

_SCRIPTS = Path(__file__).parent.parent
sys.path.insert(0, str(_SCRIPTS))  # for pipeline.py at scripts/ root
sys.path.insert(0, str(_SCRIPTS / "src"))
sys.path.insert(0, str(_SCRIPTS / "src" / "cli"))
sys.path.insert(0, str(Path(__file__).parent / "helpers"))
