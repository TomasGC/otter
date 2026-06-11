#!/usr/bin/env python3
"""Subprocess abstraction for dependency injection."""

import subprocess
from subprocess import CompletedProcess, Popen
from typing import Protocol, runtime_checkable


@runtime_checkable
class SubprocessRunner(Protocol):
    def run(self, cmd: list[str], **kwargs) -> CompletedProcess: ...
    def popen(self, cmd: list[str], **kwargs) -> Popen: ...


class RealSubprocessRunner:
    """Production subprocess runner — thin pass-through."""

    def run(self, cmd: list[str], **kwargs) -> CompletedProcess:
        return subprocess.run(cmd, **kwargs)

    def popen(self, cmd: list[str], **kwargs) -> Popen:
        return subprocess.Popen(cmd, **kwargs)
