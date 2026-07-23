#!/usr/bin/env python3
"""Fake subprocess runner for dependency injection in tests."""

import subprocess
from dataclasses import dataclass, field


@dataclass
class FakeResult:
    """Minimal CompletedProcess stand-in."""

    returncode: int = 0
    stdout: str = ""
    stderr: str = ""
    args: list = field(default_factory=list)


class FakeStdout:
    """Minimal stand-in for Popen.stdout — iterable and closeable, like a real pipe."""

    def __init__(self, lines: list[str]) -> None:
        self._lines = iter(lines)
        self.closed = False

    def __iter__(self):
        return self._lines

    def close(self) -> None:
        self.closed = True


class FakePopen:
    """Minimal Popen stand-in for streaming output tests."""

    def __init__(self, lines: list[str] = None, returncode: int = 0) -> None:
        self.returncode = returncode
        self.stdout = FakeStdout(lines or ["BUILD SUCCESSFUL\n"])

    def wait(self, timeout: int = None) -> int:
        return self.returncode

    def kill(self) -> None:
        pass


class FakeSubprocessRunner:
    """
    Injectable subprocess runner that records calls and returns staged responses.

    Usage:
        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0, stdout="List of devices attached\\nABCD\\tdevice\\n")
        mgr = AdbManager(runner)
        devices = mgr.get_connected()
        assert runner.called_with("adb", "devices")
    """

    def __init__(self) -> None:
        self.calls: list[list[str]] = []
        self._run_queue: list[FakeResult] = []
        self._popen_lines: list[str] = ["BUILD SUCCESSFUL\n"]
        self._popen_returncode: int = 0
        self.last_popen: "FakePopen | None" = None

    # --- configuration API ---

    def add_run(self, returncode: int = 0, stdout: str = "", stderr: str = "") -> "FakeSubprocessRunner":
        """Queue a response for the next run() call. Chainable."""
        self._run_queue.append(FakeResult(returncode, stdout, stderr))
        return self

    def set_popen(self, lines: list[str], returncode: int = 0) -> "FakeSubprocessRunner":
        """Configure the next popen() call's streamed output lines."""
        self._popen_lines = lines
        self._popen_returncode = returncode
        return self

    # --- SubprocessRunner protocol ---

    def run(self, cmd: list[str], **kwargs) -> FakeResult:
        self.calls.append(list(cmd))
        resp = self._run_queue.pop(0) if self._run_queue else FakeResult()
        resp.args = list(cmd)
        if kwargs.get("check") and resp.returncode != 0:
            raise subprocess.CalledProcessError(resp.returncode, cmd, resp.stderr)
        return resp

    def popen(self, cmd: list[str], **kwargs) -> FakePopen:
        self.calls.append(list(cmd))
        self.last_popen = FakePopen(list(self._popen_lines), self._popen_returncode)
        return self.last_popen

    # --- query helpers ---

    @property
    def call_count(self) -> int:
        return len(self.calls)

    def called_with(self, *args: str) -> bool:
        """Return True if any recorded call contained all given tokens."""
        return any(all(a in cmd for a in args) for cmd in self.calls)

    def calls_for(self, *args: str) -> list[list[str]]:
        """Return all calls that contained all given tokens."""
        return [cmd for cmd in self.calls if all(a in cmd for a in args)]

    def last_call(self) -> list[str]:
        return self.calls[-1] if self.calls else []
