"""Unit tests for CoverageAction."""

from unittest.mock import MagicMock

import pytest

from cli.actions.coverage import CoverageAction

pytestmark = pytest.mark.unit


def _action(tmp_path, gradle=None):
    runner = MagicMock()
    g = gradle or MagicMock()
    g.run_task.return_value = True
    return CoverageAction(runner, project_root=tmp_path, gradle=g), g


def _write_report(tmp_path, covered: int, missed: int) -> None:
    report_dir = tmp_path / "app" / "build" / "reports" / "kover"
    report_dir.mkdir(parents=True, exist_ok=True)
    xml = (
        '<?xml version="1.0" ?>\n'
        '<report name="Kover">\n'
        f'  <counter type="LINE" missed="{missed}" covered="{covered}"/>\n'
        "</report>\n"
    )
    (report_dir / "reportDebug.xml").write_text(xml)


class TestRun:
    def test_returns_0_when_above_threshold(self, tmp_path):
        action, _ = _action(tmp_path)
        covered, missed = 820, 180  # 82%
        _write_report(tmp_path, covered, missed)
        assert action.run() == 0

    def test_returns_1_when_below_threshold(self, tmp_path):
        action, _ = _action(tmp_path)
        covered, missed = 790, 210  # 79%
        _write_report(tmp_path, covered, missed)
        assert action.run() == 1

    def test_exact_threshold_passes(self, tmp_path):
        action, _ = _action(tmp_path)
        covered, missed = 800, 200  # exactly 80%
        _write_report(tmp_path, covered, missed)
        assert action.run() == 0

    def test_returns_1_when_gradle_task_fails(self, tmp_path):
        gradle = MagicMock()
        gradle.run_task.return_value = False
        action, _ = _action(tmp_path, gradle=gradle)
        assert action.run() == 1

    def test_returns_1_when_report_missing(self, tmp_path):
        action, _ = _action(tmp_path)
        assert action.run() == 1

    def test_calls_kover_xml_task(self, tmp_path):
        gradle = MagicMock()
        gradle.run_task.return_value = False
        action, _ = _action(tmp_path, gradle=gradle)
        action.run()
        gradle.run_task.assert_called_once_with("koverXmlReportDebug")
