#!/usr/bin/env python3
"""Unit tests for CorruptedArchives — format-agnostic byte mutation of a valid archive."""

from pathlib import Path

from cli.archive_scenarios.base import ArchiveScenario
from cli.archive_scenarios.corrupted import CorruptedArchives


def _make_source(tmp_path: Path, name: str, content: bytes) -> Path:
    path = tmp_path / name
    path.write_bytes(content)
    return path


class TestCorruptedArchivesIsAScenario:
    def test_is_an_archive_scenario(self, tmp_path):
        scenario = CorruptedArchives({}, tmp_path)
        assert isinstance(scenario, ArchiveScenario)


class TestCorruptedArchivesCreateAll:
    def test_result_keys_match_source_names(self, tmp_path):
        zip_src = _make_source(tmp_path, "test_archive.zip", b"PK\x03\x04" + b"x" * 100)
        rar_src = _make_source(tmp_path, "test_archive.rar", b"Rar!\x1a\x07\x00" + b"y" * 100)
        results = CorruptedArchives({"zip": zip_src, "rar": rar_src}, tmp_path).create_all()
        assert set(results.keys()) == {"zip", "rar"}

    def test_produces_a_file_for_each_valid_source(self, tmp_path):
        src = _make_source(tmp_path, "test_archive.rar", b"Rar!\x1a\x07\x00" + b"y" * 100)
        results = CorruptedArchives({"rar": src}, tmp_path).create_all()
        assert results["rar"] is not None
        assert results["rar"].exists()

    def test_output_filename_is_prefixed(self, tmp_path):
        src = _make_source(tmp_path, "test_archive.rar", b"Rar!\x1a\x07\x00" + b"y" * 100)
        results = CorruptedArchives({"rar": src}, tmp_path).create_all()
        assert results["rar"].name == "corrupted_test_archive.rar"

    def test_preserves_multi_dot_extension(self, tmp_path):
        src = _make_source(tmp_path, "test_archive.tar.gz", b"\x1f\x8b" + b"z" * 100)
        results = CorruptedArchives({"tar.gz": src}, tmp_path).create_all()
        assert results["tar.gz"].name == "corrupted_test_archive.tar.gz"

    def test_output_is_shorter_than_source(self, tmp_path):
        src = _make_source(tmp_path, "test_archive.7z", b"7z\xbc\xaf\x27\x1c" + b"w" * 200)
        results = CorruptedArchives({"7z": src}, tmp_path).create_all()
        assert results["7z"].stat().st_size < src.stat().st_size

    def test_output_bytes_differ_from_source(self, tmp_path):
        content = b"Rar!\x1a\x07\x00" + b"y" * 200
        src = _make_source(tmp_path, "test_archive.rar", content)
        results = CorruptedArchives({"rar": src}, tmp_path).create_all()
        corrupted_bytes = results["rar"].read_bytes()
        assert corrupted_bytes != content[: len(corrupted_bytes)]

    def test_handles_very_small_source_without_crashing(self, tmp_path):
        src = _make_source(tmp_path, "test_archive.gz", b"\x1f")
        results = CorruptedArchives({"gz": src}, tmp_path).create_all()
        assert results["gz"] is not None
        assert results["gz"].exists()

    def test_returns_none_for_missing_source(self, tmp_path):
        results = CorruptedArchives({"rpa": None}, tmp_path).create_all()
        assert results["rpa"] is None

    def test_returns_none_for_nonexistent_source_path(self, tmp_path):
        missing = tmp_path / "does_not_exist.zip"
        results = CorruptedArchives({"zip": missing}, tmp_path).create_all()
        assert results["zip"] is None

    def test_skips_when_output_already_exists(self, tmp_path):
        src = _make_source(tmp_path, "test_archive.rar", b"Rar!\x1a\x07\x00" + b"y" * 100)
        sentinel = b"EXISTING_CORRUPTED"
        existing = tmp_path / "corrupted_test_archive.rar"
        existing.write_bytes(sentinel)
        results = CorruptedArchives({"rar": src}, tmp_path).create_all()
        assert results["rar"] == existing
        assert existing.read_bytes() == sentinel
