#!/usr/bin/env python3
"""Unit tests for EmptyArchives — reuses Perfect's format classes against an empty template dir."""

import tarfile
import zipfile

from fake_subprocess import FakeSubprocessRunner

from cli.archive_scenarios.base import ArchiveScenario
from cli.archive_scenarios.empty import EmptyArchives


class TestEmptyArchivesIsAScenario:
    def test_is_an_archive_scenario(self, tmp_path):
        scenario = EmptyArchives(FakeSubprocessRunner(), tmp_path / "out", tmp_path / "template")
        assert isinstance(scenario, ArchiveScenario)


def _out(tmp_path):
    out = tmp_path / "out"
    out.mkdir()
    return out


class TestEmptyArchivesCreateAll:
    def test_creates_template_dir_if_missing(self, tmp_path):
        template = tmp_path / "does_not_exist_yet"
        EmptyArchives(FakeSubprocessRunner(), _out(tmp_path), template).create_all()
        assert template.is_dir()

    def test_creates_empty_zip(self, tmp_path):
        out = _out(tmp_path)
        EmptyArchives(FakeSubprocessRunner(), out, tmp_path / "template").create_all()
        with zipfile.ZipFile(out / "empty_test_archive.zip") as zf:
            assert zf.namelist() == []

    def test_creates_empty_tar(self, tmp_path):
        # tarfile.add() always includes an entry for the directory itself, even with
        # zero children — unlike ZIP, TAR has no way to omit the implicit root entry.
        out = _out(tmp_path)
        EmptyArchives(FakeSubprocessRunner(), out, tmp_path / "template").create_all()
        with tarfile.open(out / "empty_test_archive.tar") as tf:
            assert [m for m in tf.getmembers() if m.isfile()] == []

    def test_creates_empty_rpa(self, tmp_path):
        out = _out(tmp_path)
        EmptyArchives(FakeSubprocessRunner(), out, tmp_path / "template").create_all()
        assert (out / "empty_test_archive.rpa").exists()

    def test_output_filenames_use_empty_prefix(self, tmp_path):
        out = _out(tmp_path)
        results = EmptyArchives(FakeSubprocessRunner(), out, tmp_path / "template").create_all()
        assert results["zip"].name == "empty_test_archive.zip"
        assert results["tar.gz"].name == "empty_test_archive.tar.gz"

    def test_gzip_returns_none_since_it_needs_a_source_file(self, tmp_path):
        # GZIP wraps exactly one file's bytes; a template with zero files has nothing
        # to wrap, unlike ZIP/TAR which can represent "zero entries" directly.
        out = _out(tmp_path)
        results = EmptyArchives(FakeSubprocessRunner(), out, tmp_path / "template").create_all()
        assert results["gz"] is None
