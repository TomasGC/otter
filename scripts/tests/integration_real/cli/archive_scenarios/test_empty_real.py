#!/usr/bin/env python3
"""Integration-real tests for EmptyArchives — real Docker/7z, no mocks.

Regression coverage for a real bug found by running the pipeline for real: the rar CLI
refuses to create an archive from zero files (exit 10, "WARNING: No files"), which
RarDockerFormat.create() must detect and skip gracefully rather than error out.
"""

import subprocess
import zipfile

import pytest

from cli.archive_scenarios.empty import EmptyArchives
from common.subprocess_runner import RealSubprocessRunner

pytestmark = [pytest.mark.integration_real, pytest.mark.slow]


def _is_docker_available() -> bool:
    try:
        result = subprocess.run(["docker", "info"], capture_output=True, timeout=5)
        return result.returncode == 0
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return False


@pytest.mark.skipif(not _is_docker_available(), reason="Docker not available")
class TestEmptyArchivesReal:
    def test_rar_returns_none_without_crashing(self, docker_tmp):
        out = docker_tmp / "out"
        out.mkdir()
        template = docker_tmp / "template_empty"

        results = EmptyArchives(RealSubprocessRunner(), out, template).create_all()

        assert results["rar"] is None
        assert not (out / "empty_test_archive.rar").exists()

    def test_zip_is_a_real_empty_archive(self, docker_tmp):
        out = docker_tmp / "out"
        out.mkdir()
        template = docker_tmp / "template_empty"

        results = EmptyArchives(RealSubprocessRunner(), out, template).create_all()

        with zipfile.ZipFile(results["zip"]) as zf:
            assert zf.namelist() == []

    def test_seven_zip_is_a_real_valid_archive(self, docker_tmp):
        out = docker_tmp / "out"
        out.mkdir()
        template = docker_tmp / "template_empty"

        results = EmptyArchives(RealSubprocessRunner(), out, template).create_all()

        assert results["7z"] is not None
        assert results["7z"].exists()
        assert results["7z"].stat().st_size > 0
