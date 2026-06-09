#!/usr/bin/env python3
"""File system utilities."""

from pathlib import Path


def get_project_root() -> Path:
    """Get project root directory."""
    return Path(__file__).parent.parent.parent.parent
