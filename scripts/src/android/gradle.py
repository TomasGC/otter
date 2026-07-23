#!/usr/bin/env python3
"""Gradle build utilities."""

import subprocess
import sys
from pathlib import Path
from typing import Optional

from common.console import log
from common.subprocess_runner import SubprocessRunner


class GradleRunner:
    """Runs Gradle tasks via injected subprocess runner."""

    def __init__(
        self,
        runner: SubprocessRunner,
        project_root: Path,
        platform: str = sys.platform,
    ) -> None:
        self._runner = runner
        self._project_root = project_root
        self._platform = platform

    def get_wrapper_path(self) -> str:
        if self._platform == "win32":
            return str(self._project_root / "gradlew.bat")
        return str(self._project_root / "gradlew")

    def run_task(self, task: str, timeout: int = 600, extra_args: Optional[list[str]] = None) -> bool:
        try:
            command = [self.get_wrapper_path(), task] + (extra_args or [])
            process = self._runner.popen(
                command,
                cwd=self._project_root,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
            )
            for line in process.stdout:
                log(line, end="")
            process.stdout.close()
            process.wait(timeout=timeout)
            if process.returncode != 0:
                log(f"Gradle task '{task}' failed with exit code {process.returncode}")
                return False
            return True
        except subprocess.TimeoutExpired:
            process.kill()
            log(f"Gradle task '{task}' timed out after {timeout}s")
            return False
        except Exception as e:
            log(f"Gradle task '{task}' failed: {e}")
            return False
