#!/usr/bin/env python3
"""Gradle build utilities."""

import subprocess
import sys
from pathlib import Path

# Import from common using relative import
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))
from common.console import log
from common.file_utils import get_project_root


def get_gradle_wrapper() -> str:
    """Get Gradle wrapper command for current platform."""
    project_root = get_project_root()
    if sys.platform == "win32":
        return str(project_root / "gradlew.bat")
    else:
        return str(project_root / "gradlew")


def run_gradle_task(task: str, timeout: int = 600) -> bool:
    """
    Run Gradle task with live output (line by line).

    Args:
        task: Gradle task name (e.g., "assembleDebug")
        timeout: Timeout in seconds (default: 600 = 10 minutes)

    Returns:
        True if successful, False otherwise
    """
    gradle = get_gradle_wrapper()
    project_root = get_project_root()

    try:
        # Run with live output, line by line
        process = subprocess.Popen(
            [gradle, task],
            cwd=project_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1
        )

        # Stream output line by line
        for line in process.stdout:
            log(line, end='')

        process.wait(timeout=timeout)

        if process.returncode != 0:
            log(f"❌ Gradle task '{task}' failed with exit code {process.returncode}")
            return False

        return True
    except subprocess.TimeoutExpired:
        process.kill()
        log(f"❌ Gradle task '{task}' timed out after {timeout}s")
        return False
    except Exception as e:
        log(f"❌ Gradle task '{task}' failed: {e}")
        return False
