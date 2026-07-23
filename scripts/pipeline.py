#!/usr/bin/env python3
"""Local CI pipeline — mirrors Condor's kotlin/python push-ci.yml.

Each job runs `python manage.py <cmd>` and starts as soon as its own `needs`
pass, matching GitHub Actions' needs: semantics.  Independent job chains run
in parallel via threads; a job only blocks on its declared dependencies, not on
unrelated jobs.

Usage:
    python scripts/pipeline.py                   # full pipeline
    python scripts/pipeline.py validate          # specific job (+ transitive deps)
    python scripts/pipeline.py kotlin-build kotlin-instrumented
"""

import subprocess
import sys
import threading
from pathlib import Path
from typing import Optional

MANAGE = [sys.executable, str(Path(__file__).parent / "manage.py")]

KOTLIN_JOBS: list[dict] = [
    {"name": "validate",            "cmd": ["validate"],                   "needs": []},
    {"name": "kotlin-lint",         "cmd": ["lint", "kotlin"],             "needs": []},
    {"name": "kotlin-deps",         "cmd": ["lint", "deps"],               "needs": []},
    {"name": "kotlin-unit-tests",   "cmd": ["test", "unit"],               "needs": ["kotlin-lint"]},
    {"name": "kotlin-integ-mocks",  "cmd": ["test", "integration-mocks"],  "needs": ["kotlin-unit-tests"]},
    {"name": "kotlin-integ-reals",  "cmd": ["test", "integration-reals"],  "needs": ["kotlin-integ-mocks"]},
    {"name": "kotlin-build",        "cmd": ["build", "--no-install"],      "needs": ["kotlin-integ-reals"]},
    {"name": "kotlin-instrumented", "cmd": ["test", "instrumented"],       "needs": ["kotlin-build"]},
    {"name": "kotlin-coverage",     "cmd": ["coverage"],                   "needs": ["kotlin-instrumented"]},
]

PYTHON_JOBS: list[dict] = [
    {"name": "python-lint",  "cmd": ["lint", "python"],  "needs": []},
    {"name": "python-tests", "cmd": ["test-scripts"],    "needs": ["python-lint"]},
]


class PipelineRunner:
    def __init__(self, jobs: list[dict]) -> None:
        self._jobs = {j["name"]: j for j in jobs}
        self._status: dict[str, Optional[bool]] = {n: None for n in self._jobs}
        self._events: dict[str, threading.Event] = {n: threading.Event() for n in self._jobs}

    def run(self) -> int:
        threads = [
            threading.Thread(target=self._run_job, args=(name,), name=name, daemon=True)
            for name in self._jobs
        ]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        return self._print_summary()

    def _run_job(self, name: str) -> None:
        job = self._jobs[name]
        for dep in job["needs"]:
            self._events[dep].wait()
        if any(self._status.get(dep) is False for dep in job["needs"]):
            print(f"[SKIP]  {name} (dependency failed)")
            self._status[name] = None
            self._events[name].set()
            return
        ok = self._execute(name, job["cmd"])
        self._status[name] = ok
        self._events[name].set()

    def _execute(self, name: str, cmd: list[str]) -> bool:
        print(f"[START] {name}")
        result = subprocess.run(MANAGE + cmd)
        ok = result.returncode == 0
        print(f"[{'PASS' if ok else 'FAIL'}]  {name}")
        return ok

    def _print_summary(self) -> int:
        print("\n=== Pipeline Summary ===")
        for name, ok in self._status.items():
            label = "PASS" if ok is True else ("FAIL" if ok is False else "SKIP")
            print(f"  [{label}]  {name}")
        return 1 if any(v is False for v in self._status.values()) else 0


def _has_python_changes() -> bool:
    """Return True if branch has any Python file modifications."""
    repo_root = Path(__file__).parent.parent
    cmds = [
        ["git", "diff", "--name-only", "main...HEAD"],
        ["git", "diff", "--name-only"],
        ["git", "diff", "--name-only", "--cached"],
    ]
    all_files = ""
    for cmd in cmds:
        try:
            r = subprocess.run(cmd, capture_output=True, text=True, cwd=repo_root)
            all_files += r.stdout
        except Exception:
            return True  # fail-safe: include python steps when detection fails
    return any(f.endswith(".py") for f in all_files.splitlines() if f)


def _build_pipeline(include_python: bool) -> list[dict]:
    return KOTLIN_JOBS + (PYTHON_JOBS if include_python else [])


def _resolve_jobs(requested: list[str], pipeline: list[dict]) -> list[dict]:
    """Return requested jobs + their transitive dependencies, in original order."""
    all_jobs = {j["name"]: j for j in pipeline}
    resolved: set[str] = set()

    def add(name: str) -> None:
        if name in resolved:
            return
        if name not in all_jobs:
            raise ValueError(f"Unknown job: {name}")
        resolved.add(name)
        for dep in all_jobs[name]["needs"]:
            add(dep)

    for r in requested:
        add(r)
    return [j for j in pipeline if j["name"] in resolved]


def main(argv: list[str] | None = None) -> int:
    argv = argv if argv is not None else sys.argv[1:]
    include_python = _has_python_changes()
    if include_python:
        print("[INFO]  Python changes detected — including python pipeline steps")
    pipeline = _build_pipeline(include_python)
    if argv:
        try:
            jobs = _resolve_jobs(argv, pipeline)
        except ValueError as e:
            print(f"ERROR: {e}")
            print(f"Valid jobs: {', '.join(j['name'] for j in pipeline)}")
            return 1
    else:
        jobs = pipeline

    return PipelineRunner(jobs).run()


if __name__ == "__main__":
    sys.exit(main())
