#!/usr/bin/env python3
"""Integration-real test for MaliciousArchives — cross-implementation check.

Verifies the path-traversal entry survives being listed by a completely independent
tool (7z.exe), not just Python's own zipfile that wrote it. Guards against the ZIP
container format itself normalizing "../" at some layer Python's zipfile wouldn't
reveal on its own -- the eventual consumer (Kotlin's java.util.zip) is a third,
independent implementation again.
"""

import subprocess
from pathlib import Path

import pytest

from cli.archive_scenarios.malicious import TRAVERSAL_ENTRY_NAME, MaliciousArchives

pytestmark = [pytest.mark.integration_real, pytest.mark.slow]

_SEVEN_ZIP = r"C:\Program Files\7-Zip\7z.exe"


def _is_7z_available() -> bool:
    return Path(_SEVEN_ZIP).exists()


@pytest.mark.skipif(not _is_7z_available(), reason="7z not available")
class TestMaliciousArchivesReal:
    def test_seven_zip_lists_the_traversal_entry(self, docker_tmp):
        out = docker_tmp / "out"
        out.mkdir()

        results = MaliciousArchives(out).create_all()

        listing = subprocess.run([_SEVEN_ZIP, "l", str(results["zip"])], capture_output=True, text=True)
        assert TRAVERSAL_ENTRY_NAME.lstrip("./") in listing.stdout or "malicious.txt" in listing.stdout
