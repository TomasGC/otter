#!/usr/bin/env python3
"""Integration-real tests for DeepNestedArchives/LongFilenameArchives — real Docker/7z.

Windows has a legacy 260-character MAX_PATH limit; 100 levels of nesting or a 255-char
filename could plausibly overflow it inside the Docker-mounted volume even though the
mocked unit/integration_mock tiers can't detect that at all (they never touch a real
filesystem path long enough to matter, and never invoke a real archiver).
"""

import subprocess

import pytest

from cli.archive_scenarios.stress import DeepNestedArchives, LongFilenameArchives
from common.subprocess_runner import RealSubprocessRunner

pytestmark = [pytest.mark.integration_real, pytest.mark.slow]


def _is_docker_available() -> bool:
    try:
        result = subprocess.run(["docker", "info"], capture_output=True, timeout=5)
        return result.returncode == 0
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return False


@pytest.mark.skipif(not _is_docker_available(), reason="Docker not available")
class TestDeepNestedArchivesReal:
    def test_rar_created_for_100_levels(self, docker_tmp):
        out = docker_tmp / "out"
        out.mkdir()
        template = docker_tmp / "template_deep_nested"

        results = DeepNestedArchives(RealSubprocessRunner(), out, template, depth=100).create_all()

        assert results["rar"] is not None
        assert results["rar"].exists()
        assert results["rar"].stat().st_size > 0

    def test_seven_zip_created_for_100_levels(self, docker_tmp):
        out = docker_tmp / "out"
        out.mkdir()
        template = docker_tmp / "template_deep_nested"

        results = DeepNestedArchives(RealSubprocessRunner(), out, template, depth=100).create_all()

        assert results["7z"] is not None
        assert results["7z"].exists()
        assert results["7z"].stat().st_size > 0


@pytest.mark.skipif(not _is_docker_available(), reason="Docker not available")
class TestLongFilenameArchivesReal:
    def test_rar_created_for_255_char_filename(self, docker_tmp):
        out = docker_tmp / "out"
        out.mkdir()
        template = docker_tmp / "template_long_filename"

        results = LongFilenameArchives(RealSubprocessRunner(), out, template, max_length=255).create_all()

        assert results["rar"] is not None
        assert results["rar"].exists()
        assert results["rar"].stat().st_size > 0

    def test_seven_zip_created_for_255_char_filename(self, docker_tmp):
        out = docker_tmp / "out"
        out.mkdir()
        template = docker_tmp / "template_long_filename"

        results = LongFilenameArchives(RealSubprocessRunner(), out, template, max_length=255).create_all()

        assert results["7z"] is not None
        assert results["7z"].exists()
        assert results["7z"].stat().st_size > 0
