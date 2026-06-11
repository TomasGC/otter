#!/usr/bin/env python3
"""Unit tests for file utilities."""

import json
import sys
import unittest
from pathlib import Path

import pytest

from common.file_utils import get_project_root

class TestGetProjectRoot(unittest.TestCase):
    """Test get_project_root function."""

    def test_get_project_root_returns_path(self):
        """Should return Path object."""
        root = get_project_root()

        self.assertIsInstance(root, Path)

    def test_get_project_root_is_absolute(self):
        """Should return absolute path."""
        root = get_project_root()

        self.assertTrue(root.is_absolute())

    def test_get_project_root_exists(self):
        """Should return existing directory."""
        root = get_project_root()

        self.assertTrue(root.exists())
        self.assertTrue(root.is_dir())

    def test_get_project_root_contains_gradle(self):
        """Should point to Android project root (contains gradlew)."""
        root = get_project_root()

        # Android project must have Gradle wrapper
        self.assertTrue(
            (root / "gradlew").exists() or (root / "gradlew.bat").exists(),
            f"Project root {root} should contain gradlew or gradlew.bat"
        )

    def test_get_project_root_contains_app_module(self):
        """Should contain app module directory."""
        root = get_project_root()

        app_dir = root / "app"
        self.assertTrue(app_dir.exists(), f"Project root {root} should contain app/ directory")

    def test_get_project_root_is_consistent(self):
        """Should return same path on multiple calls."""
        root1 = get_project_root()
        root2 = get_project_root()

        self.assertEqual(root1, root2)

if __name__ == '__main__':
    unittest.main()


class TestLoadTestSettings:
    def test_reads_json_from_custom_path(self, tmp_path):
        from common.file_utils import load_test_settings
        data = {"test_archives": {"device_path": "/sdcard/otter"}}
        settings_file = tmp_path / "settings.json"
        settings_file.write_text(json.dumps(data), encoding="utf-8")
        result = load_test_settings(path=settings_file)
        assert result["test_archives"]["device_path"] == "/sdcard/otter"

    def test_raises_when_file_missing(self, tmp_path):
        from common.file_utils import load_test_settings
        with pytest.raises(FileNotFoundError):
            load_test_settings(path=tmp_path / "nonexistent.json")
