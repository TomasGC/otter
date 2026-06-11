"""Test-scripts action — run Python script tests via pytest."""

import sys
from pathlib import Path
from typing import Optional

from common.subprocess_runner import SubprocessRunner

SUITES = ["unit", "integration-mock", "integration-real", "e2e", "coverage"]

_MARK_MAP = {
    "unit": "unit",
    "integration-mock": "integration_mock",
    "integration-real": "integration_real",
    "e2e": "e2e",
}

_SCRIPTS_ROOT = Path(__file__).parent.parent.parent.parent


class TestScriptsAction:
    def __init__(self, runner: SubprocessRunner) -> None:
        self._runner = runner
        self._tests_dir = _SCRIPTS_ROOT / "tests"
        self._src_dir = _SCRIPTS_ROOT / "src"

    def run(self, suites: list[str] | None = None) -> int:
        suites = suites or []
        cmd = [sys.executable, "-m", "pytest", str(self._tests_dir)]

        if "coverage" in suites:
            cmd += [f"--cov={self._src_dir}", "--cov-report=term-missing"]
        elif suites:
            marks = " or ".join(_MARK_MAP[s] for s in suites if s in _MARK_MAP)
            if marks:
                cmd += ["-m", marks]

        result = self._runner.run(cmd)
        return result.returncode
