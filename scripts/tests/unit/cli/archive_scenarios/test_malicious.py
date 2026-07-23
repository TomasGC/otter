#!/usr/bin/env python3
"""Unit tests for MaliciousArchives — ZIP path-traversal fixture.

ZIP-only: TAR/GZIP/RPA have no equivalent extraction path in this app that would make a
path-traversal payload meaningful to test, and RAR/7z can't have arbitrary entry names
injected via the available CLI tools. Mirrors the payload shape of the Kotlin
TestArchiveHelper.createMaliciousZipWithPathTraversal() it replaces.
"""

import zipfile

from cli.archive_scenarios.base import ArchiveScenario
from cli.archive_scenarios.malicious import MaliciousArchives


class TestMaliciousArchivesIsAScenario:
    def test_is_an_archive_scenario(self, tmp_path):
        assert isinstance(MaliciousArchives(tmp_path), ArchiveScenario)


class TestMaliciousArchivesCreateAll:
    def test_result_keys_is_zip_only(self, tmp_path):
        results = MaliciousArchives(tmp_path).create_all()
        assert set(results.keys()) == {"zip"}

    def test_creates_a_file(self, tmp_path):
        results = MaliciousArchives(tmp_path).create_all()
        assert results["zip"].exists()

    def test_output_filename(self, tmp_path):
        results = MaliciousArchives(tmp_path).create_all()
        assert results["zip"].name == "malicious_test_archive.zip"

    def test_contains_path_traversal_entry(self, tmp_path):
        results = MaliciousArchives(tmp_path).create_all()
        with zipfile.ZipFile(results["zip"]) as zf:
            names = zf.namelist()
        assert any(".." in n for n in names)

    def test_also_contains_a_normal_entry(self, tmp_path):
        # A real-world malicious archive isn't ONLY the traversal entry — the extractor
        # must still succeed on everything else while rejecting just the bad entry.
        results = MaliciousArchives(tmp_path).create_all()
        with zipfile.ZipFile(results["zip"]) as zf:
            names = zf.namelist()
        assert any(".." not in n for n in names)

    def test_skips_when_output_already_exists(self, tmp_path):
        existing = tmp_path / "malicious_test_archive.zip"
        sentinel = b"EXISTING_MALICIOUS"
        existing.write_bytes(sentinel)
        results = MaliciousArchives(tmp_path).create_all()
        assert results["zip"] == existing
        assert existing.read_bytes() == sentinel
