"""Coverage action — generate Kover XML report and verify threshold."""

import sys
from pathlib import Path
from typing import Optional

from android import GradleRunner
from common.console import log
from common.file_utils import get_project_root
from common.subprocess_runner import SubprocessRunner

COVERAGE_THRESHOLD_PERCENT = 80


class CoverageAction:
    def __init__(
        self,
        runner: SubprocessRunner,
        project_root: Optional[Path] = None,
        gradle: Optional[GradleRunner] = None,
    ) -> None:
        self._runner = runner
        self._project_root = project_root or get_project_root()
        self._gradle = gradle or GradleRunner(runner, self._project_root)

    def run(self) -> int:
        if not self._gradle.run_task("koverXmlReportDebug"):
            return 1
        report = self._project_root / "app" / "build" / "reports" / "kover" / "reportDebug.xml"
        if not report.exists():
            log(f"ERROR: coverage report not found at {report}")
            return 1
        root = self._parse_xml(report)
        line_counter = next(c for c in root.findall("counter") if c.get("type") == "LINE")
        covered = int(line_counter.get("covered"))
        missed = int(line_counter.get("missed"))
        percent = covered / (covered + missed) * 100
        log(f"Line coverage: {percent:.1f}% (threshold: {COVERAGE_THRESHOLD_PERCENT}%)")
        if percent < COVERAGE_THRESHOLD_PERCENT:
            log(f"ERROR: coverage below {COVERAGE_THRESHOLD_PERCENT}% threshold")
            return 1
        return 0

    def _parse_xml(self, path: Path):
        try:
            import defusedxml.ElementTree as ET
        except ImportError:
            self._runner.run([sys.executable, "-m", "pip", "install", "-q", "defusedxml"])
            import defusedxml.ElementTree as ET
        return ET.parse(path).getroot()
