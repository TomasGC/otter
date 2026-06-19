#!/usr/bin/env python3
"""File system utilities."""

import json
from pathlib import Path
from typing import Optional


def get_project_root() -> Path:
    """Get project root directory."""
    return Path(__file__).parent.parent.parent.parent


TEST_SETTINGS_PATH = get_project_root() / "app" / "src" / "androidTest" / "assets" / "test-settings.json"


def load_test_settings(path: Optional[Path] = None) -> dict:
    p = path or TEST_SETTINGS_PATH
    with open(p, encoding="utf-8") as f:
        return json.load(f)
