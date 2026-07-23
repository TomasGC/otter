#!/usr/bin/env python3
"""Unit tests for the ArchiveScenario / ArchiveFormat / ArchiveFormatScenario abstractions."""

from pathlib import Path
from typing import Optional

from cli.archive_scenarios.base import ArchiveFormat, ArchiveFormatScenario


def _dirs(tmp_path: Path) -> tuple[Path, Path]:
    tmpl = tmp_path / "template"
    out = tmp_path / "output"
    tmpl.mkdir()
    out.mkdir()
    return tmpl, out


class _StubFormat(ArchiveFormat):
    """Minimal ArchiveFormat stub for orchestrator tests."""

    def __init__(self, output_dir: Path, template_dir: Path, fmt_name: str, result: Optional[Path] = None) -> None:
        super().__init__(output_dir, template_dir)
        self._name = fmt_name
        self._result = result
        self.called = False

    @property
    def name(self) -> str:
        return self._name

    def create(self) -> Optional[Path]:
        self.called = True
        return self._result


class TestArchiveFormatFilePrefix:
    def test_default_prefix_is_test_archive(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        fmt = _StubFormat(out, tmpl, "fmt1")
        assert fmt._output_path("zip") == out / "test_archive.zip"

    def test_custom_prefix_used_in_output_path(self, tmp_path):
        tmpl, out = _dirs(tmp_path)

        class PrefixedStub(ArchiveFormat):
            @property
            def name(self) -> str:
                return "fmt"

            def create(self) -> Optional[Path]:
                return None

        fmt = PrefixedStub(out, tmpl, file_prefix="empty_test_archive")
        assert fmt._output_path("zip") == out / "empty_test_archive.zip"


class TestArchiveFormatScenario:
    def test_calls_create_on_each_format(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        fmt1 = _StubFormat(out, tmpl, "fmt1")
        fmt2 = _StubFormat(out, tmpl, "fmt2")
        ArchiveFormatScenario([fmt1, fmt2]).create_all()
        assert fmt1.called and fmt2.called

    def test_result_keys_match_format_names(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        results = ArchiveFormatScenario(
            [
                _StubFormat(out, tmpl, "fmt1"),
                _StubFormat(out, tmpl, "fmt2"),
            ]
        ).create_all()
        assert set(results.keys()) == {"fmt1", "fmt2"}

    def test_result_includes_none_for_unavailable_format(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        results = ArchiveFormatScenario(
            [
                _StubFormat(out, tmpl, "ok", out / "file.zip"),
                _StubFormat(out, tmpl, "skip", None),
            ]
        ).create_all()
        assert results["ok"] is not None
        assert results["skip"] is None

    def test_is_an_archive_scenario(self, tmp_path):
        from cli.archive_scenarios.base import ArchiveScenario

        tmpl, out = _dirs(tmp_path)
        scenario = ArchiveFormatScenario([_StubFormat(out, tmpl, "fmt1")])
        assert isinstance(scenario, ArchiveScenario)
