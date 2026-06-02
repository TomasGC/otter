#!/usr/bin/env python3
"""Unit tests for versioning utilities."""

import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

# Add src to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent.parent / "src"))

from android.versioning import increment_version, get_apk_path


class TestIncrementVersion(unittest.TestCase):
    """Test increment_version function."""

    def test_increment_version_success(self):
        """Should increment versionCode and versionName."""
        with tempfile.NamedTemporaryFile(mode='w', suffix='.kts', delete=False, encoding='utf-8') as tmp_file:
            tmp_file.write("""
android {
    namespace = "app.otter"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.otter"
        minSdk = 26
        targetSdk = 34
        versionCode = 100
        versionName = "0.0.100"
    }
}
""")
            tmp_path = tmp_file.name

        build_gradle = Path(tmp_path)
        try:
            new_code, new_name = increment_version(build_gradle)

            self.assertEqual(new_code, 101)
            self.assertEqual(new_name, "0.0.101")

            # Verify file was updated
            content = build_gradle.read_text(encoding='utf-8')
            self.assertIn('versionCode = 101', content)
            self.assertIn('versionName = "0.0.101"', content)
        finally:
            build_gradle.unlink(missing_ok=True)

    def test_increment_version_with_different_format(self):
        """Should handle different version name formats."""
        with tempfile.NamedTemporaryFile(mode='w', suffix='.kts', delete=False, encoding='utf-8') as tmp_file:
            tmp_file.write("""
    versionCode = 50
    versionName = "1.2.50"
""")
            tmp_path = tmp_file.name

        build_gradle = Path(tmp_path)
        try:
                new_code, new_name = increment_version(build_gradle)

                self.assertEqual(new_code, 51)
                self.assertEqual(new_name, "1.2.51")
        finally:
            build_gradle.unlink(missing_ok=True)

    def test_increment_version_with_invalid_format(self):
        """Should default to 0.0.X for invalid version name format."""
        with tempfile.NamedTemporaryFile(mode='w', suffix='.kts', delete=False, encoding='utf-8') as tmp_file:
            tmp_file.write("""
    versionCode = 10
    versionName = "beta"
""")
            tmp_path = tmp_file.name

        build_gradle = Path(tmp_path)
        try:
                new_code, new_name = increment_version(build_gradle)

                self.assertEqual(new_code, 11)
                self.assertEqual(new_name, "0.0.11")
        finally:
            build_gradle.unlink(missing_ok=True)

    def test_increment_version_missing_versionCode(self):
        """Should raise ValueError when versionCode not found."""
        with tempfile.NamedTemporaryFile(mode='w', suffix='.kts', delete=False, encoding='utf-8') as tmp_file:
            tmp_file.write("""
    versionName = "0.0.1"
""")
            tmp_path = tmp_file.name

        build_gradle = Path(tmp_path)
        try:
                with self.assertRaises(ValueError) as context:
                    increment_version(build_gradle)

                self.assertIn("versionCode not found", str(context.exception))
        finally:
            build_gradle.unlink(missing_ok=True)

    def test_increment_version_missing_versionName(self):
        """Should raise ValueError when versionName not found."""
        with tempfile.NamedTemporaryFile(mode='w', suffix='.kts', delete=False, encoding='utf-8') as tmp_file:
            tmp_file.write("""
    versionCode = 10
""")
            tmp_path = tmp_file.name

        build_gradle = Path(tmp_path)
        try:
                with self.assertRaises(ValueError) as context:
                    increment_version(build_gradle)

                self.assertIn("versionName not found", str(context.exception))
        finally:
            build_gradle.unlink(missing_ok=True)

    def test_increment_version_preserves_other_content(self):
        """Should preserve all other content in file."""
        with tempfile.NamedTemporaryFile(mode='w', suffix='.kts', delete=False, encoding='utf-8') as tmp_file:
            original_content = """
android {
    namespace = "app.otter"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.otter"
        minSdk = 26
        targetSdk = 34
        versionCode = 100
        versionName = "0.0.100"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
        }
    }
}
"""
            tmp_file.write(original_content)
            tmp_path = tmp_file.name

        build_gradle = Path(tmp_path)
        try:
                increment_version(build_gradle)

                content = build_gradle.read_text(encoding='utf-8')
                self.assertIn('namespace = "app.otter"', content)
                self.assertIn('compileSdk = 34', content)
                self.assertIn('buildTypes', content)
                self.assertIn('isMinifyEnabled = true', content)
        finally:
            build_gradle.unlink(missing_ok=True)


class TestGetApkPath(unittest.TestCase):
    """Test get_apk_path function."""

    @patch('android.versioning.get_project_root')
    def test_get_apk_path_when_exists(self, mock_get_root):
        """Should return path to APK when it exists."""
        with tempfile.TemporaryDirectory() as tmpdir:
            # Create fake APK
            apk_dir = Path(tmpdir) / "app" / "build" / "outputs" / "apk" / "debug"
            apk_dir.mkdir(parents=True)
            apk_file = apk_dir / "app-debug.apk"
            apk_file.touch()

            mock_get_root.return_value = Path(tmpdir)

            result = get_apk_path("debug")

            self.assertIsNotNone(result)
            self.assertEqual(result, apk_file)

    @patch('android.versioning.get_project_root')
    def test_get_apk_path_when_directory_not_exists(self, mock_get_root):
        """Should return None when APK directory doesn't exist."""
        with tempfile.TemporaryDirectory() as tmpdir:
            mock_get_root.return_value = Path(tmpdir)

            result = get_apk_path("debug")

            self.assertIsNone(result)

    @patch('android.versioning.get_project_root')
    def test_get_apk_path_when_no_apk_files(self, mock_get_root):
        """Should return None when directory exists but no APK files."""
        with tempfile.TemporaryDirectory() as tmpdir:
            apk_dir = Path(tmpdir) / "app" / "build" / "outputs" / "apk" / "debug"
            apk_dir.mkdir(parents=True)

            mock_get_root.return_value = Path(tmpdir)

            result = get_apk_path("debug")

            self.assertIsNone(result)

    @patch('android.versioning.get_project_root')
    def test_get_apk_path_with_release_variant(self, mock_get_root):
        """Should find release APK when variant is release."""
        with tempfile.TemporaryDirectory() as tmpdir:
            apk_dir = Path(tmpdir) / "app" / "build" / "outputs" / "apk" / "release"
            apk_dir.mkdir(parents=True)
            apk_file = apk_dir / "app-release.apk"
            apk_file.touch()

            mock_get_root.return_value = Path(tmpdir)

            result = get_apk_path("release")

            self.assertIsNotNone(result)
            self.assertEqual(result, apk_file)

    @patch('android.versioning.get_project_root')
    def test_get_apk_path_returns_first_when_multiple(self, mock_get_root):
        """Should return first APK when multiple exist."""
        with tempfile.TemporaryDirectory() as tmpdir:
            apk_dir = Path(tmpdir) / "app" / "build" / "outputs" / "apk" / "debug"
            apk_dir.mkdir(parents=True)
            apk1 = apk_dir / "app-debug-1.apk"
            apk2 = apk_dir / "app-debug-2.apk"
            apk1.touch()
            apk2.touch()

            mock_get_root.return_value = Path(tmpdir)

            result = get_apk_path("debug")

            self.assertIsNotNone(result)
            self.assertIn(str(result), [str(apk1), str(apk2)])


if __name__ == '__main__':
    unittest.main()
