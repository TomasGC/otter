"""Validate action — branch name, commit messages, no-TODO, large files."""

import re
from pathlib import Path
from typing import Optional

from common.console import log
from common.file_utils import get_project_root
from common.subprocess_runner import SubprocessRunner

_COMMIT_MSG_RE = re.compile(r"^#[0-9]+: (feat|fix|refactor|test|docs|chore|style|perf):")
_DOCS_COMMIT_RE = re.compile(r"^docs:")
_BRANCH_NAME_RE = re.compile(r"^(feature|bugfix)/[0-9]+-[a-zA-Z0-9_-]+$")
_TODO_RE = re.compile(r"\b(TODO|FIXME)\b")
_LARGE_FILE_EXCLUDED_EXTS = {".jar", ".so", ".aar"}
_LARGE_FILE_LIMIT_BYTES = 500 * 1024


class ValidateAction:
    def __init__(self, runner: SubprocessRunner, project_root: Optional[Path] = None) -> None:
        self._runner = runner
        self._project_root = project_root or get_project_root()

    def run(self) -> int:
        results = [
            self._check_branch_name(),
            self._check_commit_messages(),
            self._check_no_todo(),
            self._check_large_files(),
        ]
        return 1 if False in results else 0

    def _check_branch_name(self) -> bool:
        result = self._runner.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            cwd=self._project_root,
            capture_output=True,
            text=True,
        )
        branch = result.stdout.strip()
        log(f"Validating branch name: {branch}")
        if not _BRANCH_NAME_RE.match(branch):
            log("ERROR: Invalid branch name format")
            log("Expected: feature/123-description or bugfix/123-description")
            return False
        log("SUCCESS: Branch name format valid")
        return True

    def _check_commit_messages(self) -> bool:
        result = self._runner.run(
            ["git", "log", "main..HEAD", "--format=%H %s"],
            cwd=self._project_root,
            capture_output=True,
            text=True,
        )
        commits = result.stdout.strip()
        if not commits:
            log("No new commits to validate")
            return True

        invalid = 0
        for line in commits.splitlines():
            commit_hash, _, msg = line.partition(" ")
            log(f"Checking: {msg}")
            if _COMMIT_MSG_RE.match(msg):
                log("  SUCCESS")
            elif _DOCS_COMMIT_RE.match(msg):
                files_result = self._runner.run(
                    ["git", "show", "--name-only", "--format=", commit_hash],
                    cwd=self._project_root,
                    capture_output=True,
                    text=True,
                )
                if "kanban.md" in files_result.stdout:
                    log("  SUCCESS: docs commit with kanban.md")
                else:
                    log("  INVALID: docs commit without issue number")
                    invalid += 1
            else:
                log("  INVALID: expected #123: type: description")
                invalid += 1

        if invalid > 0:
            log(f"ERROR: {invalid} invalid commit message(s)")
            return False
        log("SUCCESS: All commit messages valid")
        return True

    def _check_no_todo(self) -> bool:
        app_src = self._project_root / "app" / "src"
        matches: list[str] = []
        for kt_file in app_src.rglob("*.kt"):
            text = kt_file.read_text(encoding="utf-8", errors="ignore")
            for i, line in enumerate(text.splitlines(), 1):
                if _TODO_RE.search(line):
                    matches.append(f"{kt_file}:{i}:{line.strip()}")
        if matches:
            log("ERROR: Found TODO/FIXME comments:")
            for m in matches:
                log(m)
            return False
        log("SUCCESS: No TODO/FIXME found")
        return True

    def _check_large_files(self) -> bool:
        # Only git-tracked files — a raw filesystem walk would also see gitignored
        # generated content (archives/, .mypy_cache/, temp/) that a real CI checkout
        # never has, producing false positives.
        result = self._runner.run(
            ["git", "ls-files"],
            cwd=self._project_root,
            capture_output=True,
            text=True,
        )
        large: list[str] = []
        for rel_path in result.stdout.splitlines():
            path = self._project_root / rel_path
            if path.suffix in _LARGE_FILE_EXCLUDED_EXTS:
                continue
            try:
                if path.is_file() and path.stat().st_size > _LARGE_FILE_LIMIT_BYTES:
                    large.append(rel_path)
            except OSError:
                continue
        if large:
            log("ERROR: Large files detected (>500KB):")
            for f in large[:20]:
                log(f)
            return False
        log("SUCCESS: No large files found")
        return True
