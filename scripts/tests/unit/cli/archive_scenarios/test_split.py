#!/usr/bin/env python3
"""Unit tests for SplitSevenZipFormat, SplitRarDockerFormat, SplitArchives."""

from pathlib import Path
from unittest.mock import MagicMock

from fake_subprocess import FakeSubprocessRunner

from cli.archive_scenarios.base import ArchiveFormatScenario
from cli.archive_scenarios.split import (
    SplitArchives,
    SplitRarDockerFormat,
    SplitSevenZipFormat,
)

FAKE_7Z = "/fake/7z"


def _dirs(tmp_path: Path) -> tuple[Path, Path]:
    tmpl = tmp_path / "template"
    out = tmp_path / "output"
    tmpl.mkdir()
    out.mkdir()
    return tmpl, out


def _make_template(template_dir: Path) -> None:
    (template_dir / "file.txt").write_text("content\n", encoding="utf-8")


# ---------------------------------------------------------------------------
# SplitSevenZipFormat
# ---------------------------------------------------------------------------


class TestSplitSevenZipFormat:
    def test_skips_when_first_volume_already_exists(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        first = out / "split_7z.7z.001"
        first.write_bytes(b"existing")
        runner = FakeSubprocessRunner()

        result = SplitSevenZipFormat(out, tmpl, runner, seven_zip_path=FAKE_7Z).create()

        assert result == first
        assert runner.call_count == 0

    def test_returns_none_when_7z_not_found(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        _make_template(tmpl)
        runner = MagicMock()
        runner.run.side_effect = FileNotFoundError("7z not installed")

        result = SplitSevenZipFormat(out, tmpl, runner, seven_zip_path=FAKE_7Z).create()

        assert result is None

    def test_returns_none_on_subprocess_error(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        _make_template(tmpl)
        runner = FakeSubprocessRunner()
        runner.add_run(returncode=1, stderr="7z error")

        result = SplitSevenZipFormat(out, tmpl, runner, seven_zip_path=FAKE_7Z).create()

        assert result is None

    def test_returns_none_when_first_volume_not_produced(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        _make_template(tmpl)
        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0)  # succeeds but produces no .001 file

        result = SplitSevenZipFormat(out, tmpl, runner, seven_zip_path=FAKE_7Z).create()

        assert result is None

    def test_returns_first_volume_when_creation_succeeds(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        _make_template(tmpl)
        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0)
        first = out / "split_7z.7z.001"
        first.write_bytes(b"volume data")

        result = SplitSevenZipFormat(out, tmpl, runner, seven_zip_path=FAKE_7Z).create()

        assert result == first

    def test_uses_custom_file_prefix(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        _make_template(tmpl)
        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0)
        first = out / "custom.7z.001"
        first.write_bytes(b"data")

        result = SplitSevenZipFormat(out, tmpl, runner, seven_zip_path=FAKE_7Z, file_prefix="custom").create()

        assert result == first

    def test_name_property(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        fmt = SplitSevenZipFormat(out, tmpl, FakeSubprocessRunner(), seven_zip_path=FAKE_7Z)
        assert fmt.name == "7z_split"


# ---------------------------------------------------------------------------
# SplitRarDockerFormat
# ---------------------------------------------------------------------------


class TestSplitRarDockerFormat:
    def test_skips_when_first_volume_already_exists(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        first = out / "split_rar.part1.rar"
        first.write_bytes(b"existing")
        runner = FakeSubprocessRunner()

        result = SplitRarDockerFormat(out, tmpl, runner, dockerfile_dir=tmp_path).create()

        assert result == first
        assert runner.call_count == 0

    def test_returns_none_when_template_dir_is_empty(self, tmp_path):
        tmpl, out = _dirs(tmp_path)  # no files in tmpl
        runner = FakeSubprocessRunner()

        result = SplitRarDockerFormat(out, tmpl, runner, dockerfile_dir=tmp_path).create()

        assert result is None
        assert runner.call_count == 0

    def test_returns_none_when_docker_not_found(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        _make_template(tmpl)
        runner = MagicMock()
        runner.run.side_effect = FileNotFoundError("docker not installed")

        result = SplitRarDockerFormat(out, tmpl, runner, dockerfile_dir=tmp_path).create()

        assert result is None

    def test_returns_none_on_docker_build_error(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        _make_template(tmpl)
        runner = FakeSubprocessRunner()
        runner.add_run(returncode=1, stderr="build failed")

        result = SplitRarDockerFormat(out, tmpl, runner, dockerfile_dir=tmp_path).create()

        assert result is None

    def test_returns_none_on_docker_run_error(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        _make_template(tmpl)
        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0)  # docker build OK
        runner.add_run(returncode=1, stderr="docker run failed")

        result = SplitRarDockerFormat(out, tmpl, runner, dockerfile_dir=tmp_path).create()

        assert result is None

    def test_returns_none_when_first_volume_not_produced(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        _make_template(tmpl)
        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0)  # docker build
        runner.add_run(returncode=0)  # docker run — but no .part1.rar created

        result = SplitRarDockerFormat(out, tmpl, runner, dockerfile_dir=tmp_path).create()

        assert result is None

    def test_returns_first_volume_when_creation_succeeds(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        _make_template(tmpl)
        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0)  # docker build
        runner.add_run(returncode=0)  # docker run
        first = out / "split_rar.part1.rar"
        first.write_bytes(b"rar data")

        result = SplitRarDockerFormat(out, tmpl, runner, dockerfile_dir=tmp_path).create()

        assert result == first

    def test_to_docker_path_has_no_backslashes(self, tmp_path):
        result = SplitRarDockerFormat._to_docker_path(tmp_path)
        assert "\\" not in result

    def test_name_property(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        fmt = SplitRarDockerFormat(out, tmpl, FakeSubprocessRunner(), dockerfile_dir=tmp_path)
        assert fmt.name == "rar_split"


# ---------------------------------------------------------------------------
# SplitArchives
# ---------------------------------------------------------------------------


class TestSplitArchives:
    def test_is_an_archive_format_scenario(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        scenario = SplitArchives(FakeSubprocessRunner(), out, tmpl)
        assert isinstance(scenario, ArchiveFormatScenario)

    def test_has_7z_and_rar_formats(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        scenario = SplitArchives(FakeSubprocessRunner(), out, tmpl)
        names = {fmt.name for fmt in scenario._formats}
        assert names == {"7z_split", "rar_split"}
