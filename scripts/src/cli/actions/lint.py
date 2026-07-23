"""Lint action — kotlin and python lint checks."""

import shutil
import sys
from pathlib import Path
from typing import Optional

from android import GradleRunner
from common.console import log
from common.file_utils import get_project_root
from common.subprocess_runner import SubprocessRunner

PYLINT_FAIL_UNDER = 7
KTLINT_VERSION = "0.50.0"


class LintAction:
    def __init__(
        self,
        runner: SubprocessRunner,
        project_root: Optional[Path] = None,
        gradle: Optional[GradleRunner] = None,
    ) -> None:
        self._runner = runner
        self._project_root = project_root or get_project_root()
        self._gradle = gradle or GradleRunner(runner, self._project_root)
        self._scripts_dir = self._project_root / "scripts"

    def run(self, target: str) -> int:
        if target == "kotlin":
            return self._run_kotlin()
        if target == "python":
            return self._run_python()
        if target == "deps":
            return self._run_deps()
        log(f"ERROR: unknown lint target '{target}' (use 'kotlin', 'python', or 'deps')")
        return 1

    # -------------------------------------------------------------------------
    # Kotlin lint
    # -------------------------------------------------------------------------

    def _run_kotlin(self) -> int:
        results = [
            self._kotlin_android_lint(),
            self._kotlin_ktlint(),
            self._kotlin_detekt(),
            self._kotlin_secrets_scan(),
        ]
        return 1 if False in results else 0

    def _kotlin_android_lint(self) -> bool:
        return self._gradle.run_task("lintDebug")

    def _kotlin_detekt(self) -> bool:
        return self._gradle.run_task("detekt")

    def _kotlin_ktlint(self) -> Optional[bool]:
        # Advisory only in CI (fail-on-error=false via reviewdog) — never blocks.
        ktlint = self._ensure_ktlint()
        if ktlint is None:
            log("SKIPPED: ktlint unavailable (non-blocking in CI too)")
            return None
        kt_files = [str(f) for f in (self._project_root / "app" / "src").rglob("*.kt")]
        try:
            result = self._runner.run(["bash", str(ktlint)] + kt_files, capture_output=True, text=True)
            if result.stdout:
                log(result.stdout)
            if result.returncode != 0:
                log("ktlint found style violations (non-blocking, matches CI's fail-on-error=false)")
        except Exception as e:
            log(f"SKIPPED: ktlint failed to run ({e}) — non-blocking in CI too")
        return True

    def _ensure_ktlint(self) -> Optional[Path]:
        cache_dir = self._scripts_dir / ".cache"
        cache_dir.mkdir(parents=True, exist_ok=True)
        ktlint_path = cache_dir / "ktlint"
        if ktlint_path.exists():
            return ktlint_path
        log(f"Downloading ktlint {KTLINT_VERSION}...")
        url = f"https://github.com/pinterest/ktlint/releases/download/{KTLINT_VERSION}/ktlint"
        try:
            result = self._runner.run(["curl", "-sSLo", str(ktlint_path), url])
            if result.returncode != 0 or not ktlint_path.exists():
                return None
            return ktlint_path
        except Exception as e:
            log(f"Failed to download ktlint: {e}")
            return None

    def _kotlin_secrets_scan(self) -> Optional[bool]:
        trufflehog = shutil.which("trufflehog")
        if trufflehog is None:
            log(
                "SKIPPED: trufflehog not installed "
                "(https://github.com/trufflesecurity/trufflehog#floppy_disk-installation)"
            )
            return None
        result = self._runner.run(
            [trufflehog, "git", f"file://{self._project_root}", "--since-commit=main", "--only-verified", "--fail"],
            cwd=self._project_root,
            capture_output=True,
            text=True,
        )
        if result.stdout:
            log(result.stdout)
        if result.returncode != 0:
            log("ERROR: verified secrets found")
            return False
        log("SUCCESS: no verified secrets found")
        return True

    # -------------------------------------------------------------------------
    # Dependency vulnerability scan
    # -------------------------------------------------------------------------

    def _run_deps(self) -> int:
        results = [self._kotlin_osv_scan()]
        return 1 if False in results else 0

    def _kotlin_osv_scan(self) -> Optional[bool]:
        osv = shutil.which("osv-scanner")
        if osv is None:
            log("SKIPPED: osv-scanner not installed (https://google.github.io/osv-scanner/installation/)")
            return None
        metadata = self._project_root / "gradle" / "verification-metadata.xml"
        if not metadata.exists():
            log(
                "ERROR: gradle/verification-metadata.xml not found"
                " — run: ./gradlew --write-verification-metadata sha256"
            )
            return False
        result = self._runner.run([osv, f"--lockfile={metadata}"], cwd=self._project_root)
        if result.returncode != 0:
            log("ERROR: OSV Scanner found vulnerabilities")
            return False
        log("SUCCESS: no known vulnerabilities found")
        return True

    # -------------------------------------------------------------------------
    # Python lint
    # -------------------------------------------------------------------------

    def _run_python(self) -> int:
        results = [
            self._python_flake8(),
            self._python_style(),
            self._python_pylint(),
            self._python_mypy(),
            self._python_bandit(),
            self._python_pip_audit(),
            self._python_vulture(),
        ]
        return 1 if False in results else 0

    def _pip_install(self, *packages: str) -> None:
        self._runner.run([sys.executable, "-m", "pip", "install", "-q"] + list(packages))

    def _run_python_tool(self, cmd: list[str]) -> bool:
        result = self._runner.run(cmd, cwd=self._scripts_dir)
        return result.returncode == 0

    def _python_flake8(self) -> bool:
        self._pip_install("flake8")
        return self._run_python_tool(
            [sys.executable, "-m", "flake8", "src/", "tests/", "--max-line-length=120", "--statistics"]
        )

    def _python_style(self) -> bool:
        self._pip_install("black", "isort")
        black_ok = self._run_python_tool(
            [sys.executable, "-m", "black", "--check", "--line-length=120", "src/", "tests/"]
        )
        isort_ok = self._run_python_tool(
            [sys.executable, "-m", "isort", "--check", "--profile=black", "src/", "tests/"]
        )
        return black_ok and isort_ok

    def _python_pylint(self) -> bool:
        self._pip_install("pylint")
        self._run_python_tool([sys.executable, "-m", "pip", "install", "-q", "-r", "requirements-test.txt"])
        return self._run_python_tool(
            [
                sys.executable,
                "-m",
                "pylint",
                "src/",
                "tests/",
                f"--fail-under={PYLINT_FAIL_UNDER}",
                "--disable=C0114,C0115,C0116",
            ]
        )

    def _python_mypy(self) -> bool:
        self._pip_install("mypy")
        return self._run_python_tool(
            [sys.executable, "-m", "mypy", "src/", "--ignore-missing-imports", "--no-error-summary"]
        )

    def _python_bandit(self) -> bool:
        self._pip_install("bandit")
        return self._run_python_tool([sys.executable, "-m", "bandit", "-r", "src/", "-ll", "-q"])

    def _python_pip_audit(self) -> bool:
        self._pip_install("pip-audit")
        self._run_python_tool([sys.executable, "-m", "pip", "install", "-q", "-r", "requirements-test.txt"])
        # Scoped to declared deps — otherwise pip_audit audits every package in the ambient env.
        return self._run_python_tool([sys.executable, "-m", "pip_audit", "-r", "requirements-test.txt"])

    def _python_vulture(self) -> bool:
        self._pip_install("vulture")
        return self._run_python_tool([sys.executable, "-m", "vulture", "src/", "tests/", "--min-confidence=80"])
