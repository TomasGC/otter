#!/usr/bin/env python3
"""Unit tests for VersionManager — pure parsing methods, zero I/O."""

import pytest

from android.versioning import VersionManager

GRADLE_TEMPLATE = """\
android {
    defaultConfig {
        applicationId = "app.otter"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.0.5"
    }
}
"""

# ---------------------------------------------------------------------------
# parse_version_code
# ---------------------------------------------------------------------------


class TestParseVersionCode:
    def test_parses_simple_code(self):
        assert VersionManager.parse_version_code("versionCode = 42") == 42

    def test_parses_with_whitespace_variants(self):
        assert VersionManager.parse_version_code("versionCode=7") == 7
        assert VersionManager.parse_version_code("versionCode  =  99") == 99

    def test_parses_from_full_gradle_block(self):
        assert VersionManager.parse_version_code(GRADLE_TEMPLATE) == 5

    def test_raises_when_missing(self):
        with pytest.raises(ValueError, match="versionCode not found"):
            VersionManager.parse_version_code("no version here")

    def test_raises_on_empty_string(self):
        with pytest.raises(ValueError):
            VersionManager.parse_version_code("")


# ---------------------------------------------------------------------------
# parse_version_name
# ---------------------------------------------------------------------------


class TestParseVersionName:
    def test_parses_semver(self):
        assert VersionManager.parse_version_name('versionName = "1.2.3"') == "1.2.3"

    def test_parses_from_full_gradle_block(self):
        assert VersionManager.parse_version_name(GRADLE_TEMPLATE) == "0.0.5"

    def test_raises_when_missing(self):
        with pytest.raises(ValueError, match="versionName not found"):
            VersionManager.parse_version_name("no version name")

    def test_parses_multi_digit_parts(self):
        assert VersionManager.parse_version_name('versionName = "10.20.300"') == "10.20.300"


# ---------------------------------------------------------------------------
# compute_new_name
# ---------------------------------------------------------------------------


class TestComputeNewName:
    def test_increments_patch_component(self):
        assert VersionManager.compute_new_name("0.0.5", 6) == "0.0.6"

    def test_preserves_major_and_minor(self):
        assert VersionManager.compute_new_name("2.3.10", 11) == "2.3.11"

    def test_non_semver_falls_back_to_zero_zero_patch(self):
        assert VersionManager.compute_new_name("1.0", 5) == "0.0.5"

    def test_single_component_falls_back(self):
        assert VersionManager.compute_new_name("7", 8) == "0.0.8"

    def test_new_code_drives_patch(self):
        assert VersionManager.compute_new_name("1.0.0", 100) == "1.0.100"


# ---------------------------------------------------------------------------
# apply_version
# ---------------------------------------------------------------------------


class TestApplyVersion:
    def test_replaces_version_code(self):
        result = VersionManager.apply_version("versionCode = 1", 2, "0.0.2")
        assert "versionCode = 2" in result

    def test_replaces_version_name(self):
        result = VersionManager.apply_version('versionName = "0.0.1"', 2, "0.0.2")
        assert 'versionName = "0.0.2"' in result

    def test_preserves_surrounding_content(self):
        content = 'compileSdk = 34\nversionCode = 1\nversionName = "0.0.1"\nminSdk = 26'
        result = VersionManager.apply_version(content, 2, "0.0.2")
        assert "compileSdk = 34" in result
        assert "minSdk = 26" in result

    def test_handles_full_gradle_block(self):
        result = VersionManager.apply_version(GRADLE_TEMPLATE, 6, "0.0.6")
        assert "versionCode = 6" in result
        assert 'versionName = "0.0.6"' in result
        assert "applicationId" in result

    def test_does_not_duplicate_entries(self):
        result = VersionManager.apply_version(GRADLE_TEMPLATE, 6, "0.0.6")
        assert result.count("versionCode") == 1
        assert result.count("versionName") == 1


# ---------------------------------------------------------------------------
# Integration tests — real file I/O, uses tmp_path
# ---------------------------------------------------------------------------


class TestVersionManagerIncrement:
    def test_returns_incremented_code(self, tmp_path):
        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text(GRADLE_TEMPLATE)
        code, _ = VersionManager(tmp_path).increment(gradle)
        assert code == 6

    def test_returns_new_name(self, tmp_path):
        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text(GRADLE_TEMPLATE)
        _, name = VersionManager(tmp_path).increment(gradle)
        assert name == "0.0.6"

    def test_writes_new_code_to_file(self, tmp_path):
        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text(GRADLE_TEMPLATE)
        VersionManager(tmp_path).increment(gradle)
        assert "versionCode = 6" in gradle.read_text()

    def test_writes_new_name_to_file(self, tmp_path):
        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text(GRADLE_TEMPLATE)
        VersionManager(tmp_path).increment(gradle)
        assert 'versionName = "0.0.6"' in gradle.read_text()

    def test_preserves_rest_of_file(self, tmp_path):
        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text(GRADLE_TEMPLATE)
        VersionManager(tmp_path).increment(gradle)
        content = gradle.read_text()
        assert "applicationId" in content
        assert "minSdk = 26" in content

    def test_consecutive_increments(self, tmp_path):
        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text(GRADLE_TEMPLATE)
        vm = VersionManager(tmp_path)
        vm.increment(gradle)
        code, name = vm.increment(gradle)
        assert code == 7
        assert name == "0.0.7"

    def test_raises_on_missing_version_code(self, tmp_path):
        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text("android { }")
        with pytest.raises(ValueError):
            VersionManager(tmp_path).increment(gradle)


class TestVersionManagerGetApkPath:
    def test_returns_none_when_dir_missing(self, tmp_path):
        assert VersionManager(tmp_path).get_apk_path() is None

    def test_returns_none_when_no_apk_file(self, tmp_path):
        apk_dir = tmp_path / "app" / "build" / "outputs" / "apk" / "debug"
        apk_dir.mkdir(parents=True)
        assert VersionManager(tmp_path).get_apk_path() is None

    def test_finds_apk_in_debug_dir(self, tmp_path):
        apk_dir = tmp_path / "app" / "build" / "outputs" / "apk" / "debug"
        apk_dir.mkdir(parents=True)
        (apk_dir / "app-debug.apk").write_bytes(b"FAKE")
        result = VersionManager(tmp_path).get_apk_path()
        assert result is not None
        assert result.name == "app-debug.apk"

    def test_respects_variant_parameter(self, tmp_path):
        apk_dir = tmp_path / "app" / "build" / "outputs" / "apk" / "release"
        apk_dir.mkdir(parents=True)
        (apk_dir / "app-release.apk").write_bytes(b"FAKE")
        result = VersionManager(tmp_path).get_apk_path("release")
        assert result is not None
        assert result.name == "app-release.apk"

    def test_debug_variant_is_default(self, tmp_path):
        debug_dir = tmp_path / "app" / "build" / "outputs" / "apk" / "debug"
        debug_dir.mkdir(parents=True)
        (debug_dir / "app-debug.apk").write_bytes(b"FAKE")
        result = VersionManager(tmp_path).get_apk_path()
        assert result is not None
