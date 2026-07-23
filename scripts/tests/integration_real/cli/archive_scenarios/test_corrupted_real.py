#!/usr/bin/env python3
"""Integration-real tests for CorruptedArchives — verifies corruption is real, not
format-mistaken: a real tool must actually fail to open/test the corrupted output.

This is the direct regression target for the original bug: RAR/7z E2E tests asserted
"corrupted archive should return Failure" against a ZIP-shaped file wearing a .rar/.7z
extension, which 7-Zip-JBinding's format auto-detection opened fine anyway.
"""

import subprocess
import zipfile
from pathlib import Path

import pytest

from cli.archive_scenarios.corrupted import CorruptedArchives
from cli.archive_scenarios.perfect import RarDockerFormat, SevenZipFormat, ZipFormat
from common.subprocess_runner import RealSubprocessRunner

pytestmark = [pytest.mark.integration_real, pytest.mark.slow]

_SEVEN_ZIP = r"C:\Program Files\7-Zip\7z.exe"


def _is_docker_available() -> bool:
    try:
        result = subprocess.run(["docker", "info"], capture_output=True, timeout=5)
        return result.returncode == 0
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return False


def _is_7z_available() -> bool:
    return Path(_SEVEN_ZIP).exists()


def _make_template(template_dir: Path) -> None:
    template_dir.mkdir(parents=True, exist_ok=True)
    (template_dir / "file.txt").write_text("real content for corruption test\n" * 50, encoding="utf-8")


class TestCorruptedArchivesRealZip:
    def test_real_corrupted_zip_is_not_openable(self, docker_tmp):
        out = docker_tmp / "out"
        out.mkdir()
        template = docker_tmp / "template"
        _make_template(template)

        perfect = ZipFormat(out, template).create()
        results = CorruptedArchives({"zip": perfect}, out).create_all()

        with pytest.raises(zipfile.BadZipFile):
            zipfile.ZipFile(results["zip"])


@pytest.mark.skipif(not _is_docker_available(), reason="Docker not available")
@pytest.mark.skipif(not _is_7z_available(), reason="7z not available")
class TestCorruptedArchivesRealRar:
    def test_real_corrupted_rar_fails_7z_test(self, docker_tmp):
        out = docker_tmp / "out"
        out.mkdir()
        template = docker_tmp / "template"
        _make_template(template)

        perfect = RarDockerFormat(out, template, RealSubprocessRunner()).create()
        results = CorruptedArchives({"rar": perfect}, out).create_all()

        result = subprocess.run([_SEVEN_ZIP, "t", str(results["rar"])], capture_output=True, text=True)
        assert result.returncode != 0


@pytest.mark.skipif(not _is_7z_available(), reason="7z not available")
class TestCorruptedArchivesRealSevenZip:
    def test_real_corrupted_7z_fails_7z_test(self, docker_tmp):
        out = docker_tmp / "out"
        out.mkdir()
        template = docker_tmp / "template"
        _make_template(template)

        perfect = SevenZipFormat(out, template, RealSubprocessRunner()).create()
        results = CorruptedArchives({"7z": perfect}, out).create_all()

        result = subprocess.run([_SEVEN_ZIP, "t", str(results["7z"])], capture_output=True, text=True)
        assert result.returncode != 0
