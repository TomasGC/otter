#!/usr/bin/env python3
"""Integration test for create_all_fixture_archives — real filesystem, fake subprocess.

Uses a tiny template and a small LargeArchives file_count override (via a thin wrapper
around the real class, injected through patch's side_effect) so this runs fast while
still exercising every scenario for real — no mocked scenario classes.
"""

import zipfile
from unittest.mock import patch

import pytest
from fake_subprocess import FakeSubprocessRunner

from cli.archive_scenarios.orchestrator import create_all_fixture_archives
from cli.archive_scenarios.stress import LargeArchives

pytestmark = pytest.mark.integration_mock

SMALL_FILE_COUNT = 5


def _small_large_archives(runner, output_dir, template_dir, **kwargs):
    return LargeArchives(runner, output_dir, template_dir, file_count=SMALL_FILE_COUNT)


def _make_template(template_dir):
    template_dir.mkdir(parents=True, exist_ok=True)
    (template_dir / "file_000.txt").write_text("content 0\n", encoding="utf-8")
    (template_dir / "file_001.txt").write_text("content 1\n", encoding="utf-8")


def _run(out, template):
    with patch("cli.archive_scenarios.orchestrator.LargeArchives", side_effect=_small_large_archives):
        create_all_fixture_archives(FakeSubprocessRunner(), out, template)


class TestCreateAllFixtureArchivesRealFilesystem:
    def test_produces_expected_output_files(self, tmp_path):
        out = tmp_path / "out"
        out.mkdir()
        template = tmp_path / "template"
        _make_template(template)

        _run(out, template)

        expected_prefixes = [
            "test_archive",
            "corrupted_test_archive",
            "empty_test_archive",
            "malicious_test_archive",
            "large_test_archive",
            "deep_nested_test_archive",
            "long_filename_test_archive",
        ]
        created_names = {f.name for f in out.iterdir()}
        for prefix in expected_prefixes:
            assert any(name.startswith(prefix) for name in created_names), f"missing output for {prefix}"

    def test_malicious_zip_has_traversal_entry(self, tmp_path):
        out = tmp_path / "out"
        out.mkdir()
        template = tmp_path / "template"
        _make_template(template)

        _run(out, template)

        with zipfile.ZipFile(out / "malicious_test_archive.zip") as zf:
            names = zf.namelist()
        assert any(".." in n for n in names)

    def test_corrupted_zip_is_shorter_than_perfect_zip(self, tmp_path):
        out = tmp_path / "out"
        out.mkdir()
        template = tmp_path / "template"
        _make_template(template)

        _run(out, template)

        perfect_size = (out / "test_archive.zip").stat().st_size
        corrupted_size = (out / "corrupted_test_archive.zip").stat().st_size
        assert corrupted_size < perfect_size

    def test_large_zip_has_requested_file_count(self, tmp_path):
        out = tmp_path / "out"
        out.mkdir()
        template = tmp_path / "template"
        _make_template(template)

        _run(out, template)

        with zipfile.ZipFile(out / "large_test_archive.zip") as zf:
            assert len(zf.namelist()) == SMALL_FILE_COUNT
