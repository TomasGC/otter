#!/usr/bin/env python3
"""Unit tests for Large/DeepNested/LongFilename stress archive scenarios."""

import random
import tarfile
import zipfile

from fake_subprocess import FakeSubprocessRunner

from cli.archive_scenarios.base import ArchiveScenario
from cli.archive_scenarios.stress import (
    DeepNestedArchives,
    LargeArchives,
    LongFilenameArchives,
)


def _out(tmp_path):
    out = tmp_path / "out"
    out.mkdir()
    return out


class TestLargeArchives:
    def test_is_an_archive_scenario(self, tmp_path):
        scenario = LargeArchives(FakeSubprocessRunner(), _out(tmp_path), tmp_path / "template", file_count=3)
        assert isinstance(scenario, ArchiveScenario)

    def test_creates_zip_with_requested_file_count(self, tmp_path):
        out = _out(tmp_path)
        LargeArchives(
            FakeSubprocessRunner(), out, tmp_path / "template", file_count=25, rng=random.Random(0)
        ).create_all()
        with zipfile.ZipFile(out / "large_test_archive.zip") as zf:
            assert len(zf.namelist()) == 25

    def test_output_filenames_use_large_prefix(self, tmp_path):
        out = _out(tmp_path)
        results = LargeArchives(
            FakeSubprocessRunner(), out, tmp_path / "template", file_count=5, rng=random.Random(0)
        ).create_all()
        assert results["zip"].name == "large_test_archive.zip"


class TestDeepNestedArchives:
    def test_is_an_archive_scenario(self, tmp_path):
        scenario = DeepNestedArchives(FakeSubprocessRunner(), _out(tmp_path), tmp_path / "template", depth=3)
        assert isinstance(scenario, ArchiveScenario)

    def test_creates_tar_with_requested_depth(self, tmp_path):
        out = _out(tmp_path)
        DeepNestedArchives(
            FakeSubprocessRunner(), out, tmp_path / "template", depth=4, rng=random.Random(0)
        ).create_all()
        with tarfile.open(out / "deep_nested_test_archive.tar") as tf:
            names = tf.getnames()
        assert any("level_1/level_2/level_3/level_4" in n for n in names)

    def test_output_filenames_use_deep_nested_prefix(self, tmp_path):
        out = _out(tmp_path)
        results = DeepNestedArchives(
            FakeSubprocessRunner(), out, tmp_path / "template", depth=2, rng=random.Random(0)
        ).create_all()
        assert results["tar"].name == "deep_nested_test_archive.tar"


class TestLongFilenameArchives:
    def test_is_an_archive_scenario(self, tmp_path):
        scenario = LongFilenameArchives(FakeSubprocessRunner(), _out(tmp_path), tmp_path / "template", max_length=50)
        assert isinstance(scenario, ArchiveScenario)

    def test_creates_zip_with_long_filename_entry(self, tmp_path):
        out = _out(tmp_path)
        LongFilenameArchives(
            FakeSubprocessRunner(), out, tmp_path / "template", max_length=200, rng=random.Random(0)
        ).create_all()
        with zipfile.ZipFile(out / "long_filename_test_archive.zip") as zf:
            names = zf.namelist()
        assert len(names) == 1
        assert len(names[0]) == 200

    def test_output_filenames_use_long_filename_prefix(self, tmp_path):
        out = _out(tmp_path)
        results = LongFilenameArchives(
            FakeSubprocessRunner(), out, tmp_path / "template", max_length=100, rng=random.Random(0)
        ).create_all()
        assert results["zip"].name == "long_filename_test_archive.zip"
