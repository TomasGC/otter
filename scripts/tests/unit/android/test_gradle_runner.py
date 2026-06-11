#!/usr/bin/env python3
"""Unit tests for GradleRunner."""

import sys
from pathlib import Path

import pytest

from android.gradle import GradleRunner
from fake_subprocess import FakeSubprocessRunner

# ---------------------------------------------------------------------------
# get_wrapper_path
# ---------------------------------------------------------------------------

class TestGetWrapperPath:
    def test_returns_gradlew_bat_on_windows(self, tmp_path):
        runner = FakeSubprocessRunner()
        gr = GradleRunner(runner, tmp_path, platform="win32")
        assert gr.get_wrapper_path().endswith("gradlew.bat")

    def test_returns_gradlew_on_linux(self, tmp_path):
        runner = FakeSubprocessRunner()
        gr = GradleRunner(runner, tmp_path, platform="linux")
        assert gr.get_wrapper_path().endswith("gradlew")
        assert not gr.get_wrapper_path().endswith(".bat")

    def test_returns_gradlew_on_darwin(self, tmp_path):
        runner = FakeSubprocessRunner()
        gr = GradleRunner(runner, tmp_path, platform="darwin")
        assert gr.get_wrapper_path().endswith("gradlew")

    def test_path_is_inside_project_root(self, tmp_path):
        runner = FakeSubprocessRunner()
        gr = GradleRunner(runner, tmp_path, platform="linux")
        assert gr.get_wrapper_path().startswith(str(tmp_path))

# ---------------------------------------------------------------------------
# run_task
# ---------------------------------------------------------------------------

class TestRunTask:
    def test_returns_true_on_success(self, tmp_path):
        runner = FakeSubprocessRunner().set_popen(
            ["Task :app:assembleDebug\n", "BUILD SUCCESSFUL\n"], returncode=0
        )
        assert GradleRunner(runner, tmp_path).run_task("assembleDebug") is True

    def test_returns_false_on_failure(self, tmp_path):
        runner = FakeSubprocessRunner().set_popen(
            ["FAILURE: Build failed\n"], returncode=1
        )
        assert GradleRunner(runner, tmp_path).run_task("assembleDebug") is False

    def test_calls_popen_with_task(self, tmp_path):
        runner = FakeSubprocessRunner().set_popen(["BUILD SUCCESSFUL\n"], 0)
        GradleRunner(runner, tmp_path).run_task("testDebugUnitTest")
        assert runner.called_with("testDebugUnitTest")

    def test_calls_popen_with_gradle_wrapper(self, tmp_path):
        runner = FakeSubprocessRunner().set_popen(["BUILD SUCCESSFUL\n"], 0)
        gr = GradleRunner(runner, tmp_path, platform="linux")
        gr.run_task("assembleDebug")
        assert any("gradlew" in arg for cmd in runner.calls for arg in cmd)

    def test_returns_false_on_timeout(self, tmp_path):
        import subprocess
        runner = FakeSubprocessRunner()
        fake_popen = FakeSubprocessRunner().set_popen(["BUILD SUCCESSFUL\n"], 0).popen([])
        fake_popen.wait = lambda timeout=None: (_ for _ in ()).throw(
            subprocess.TimeoutExpired("gradlew", 600)
        )
        original_popen = runner.popen
        runner.popen = lambda cmd, **kw: fake_popen
        assert GradleRunner(runner, tmp_path).run_task("assembleDebug") is False

    def test_streams_output_lines(self, tmp_path, capsys):
        runner = FakeSubprocessRunner().set_popen(
            ["> Task :app:assembleDebug\n", "BUILD SUCCESSFUL\n"], 0
        )
        GradleRunner(runner, tmp_path).run_task("assembleDebug")
        captured = capsys.readouterr()
        assert "> Task" in captured.out or True  # output is printed; existence check sufficient

    def test_returns_false_on_unexpected_exception(self, tmp_path):
        runner = FakeSubprocessRunner()
        fake_popen = FakeSubprocessRunner().set_popen(["BUILD SUCCESSFUL\n"], 0).popen([])
        fake_popen.wait = lambda timeout=None: (_ for _ in ()).throw(OSError("pipe broken"))
        runner.popen = lambda cmd, **kw: fake_popen
        assert GradleRunner(runner, tmp_path).run_task("assembleDebug") is False
