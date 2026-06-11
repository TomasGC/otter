"""Unit tests for TestScriptsAction."""

import pytest
from fake_subprocess import FakeSubprocessRunner

from cli.actions.test_scripts import SUITES
from cli.actions.test_scripts import TestScriptsAction as ScriptsTestAction

pytestmark = pytest.mark.unit


def make_action():
    runner = FakeSubprocessRunner()
    runner.add_run(returncode=0)
    return ScriptsTestAction(runner), runner


def pytest_flags(cmd: list[str]) -> list[str]:
    """Return the pytest flags portion of the cmd (after 'python -m pytest <tests_dir>')."""
    return cmd[4:]


class TestSuitesConstant:
    def test_contains_expected_suites(self):
        for s in ["unit", "integration-mock", "integration-real", "e2e", "coverage"]:
            assert s in SUITES


class TestTestScriptsActionRun:
    def test_no_suites_runs_all_tests(self):
        action, runner = make_action()
        action.run()
        flags = pytest_flags(runner.last_call())
        assert "pytest" in " ".join(runner.last_call())
        assert "-m" not in flags
        assert "--cov" not in " ".join(flags)

    def test_unit_suite_adds_unit_mark(self):
        action, runner = make_action()
        action.run(suites=["unit"])
        flags = pytest_flags(runner.last_call())
        assert "-m" in flags
        idx = flags.index("-m")
        assert "unit" in flags[idx + 1]

    def test_integration_mock_adds_mark(self):
        action, runner = make_action()
        action.run(suites=["integration-mock"])
        flags = pytest_flags(runner.last_call())
        assert "-m" in flags
        idx = flags.index("-m")
        assert "integration_mock" in flags[idx + 1]

    def test_integration_real_adds_mark(self):
        action, runner = make_action()
        action.run(suites=["integration-real"])
        flags = pytest_flags(runner.last_call())
        assert "-m" in flags
        idx = flags.index("-m")
        assert "integration_real" in flags[idx + 1]

    def test_e2e_adds_mark(self):
        action, runner = make_action()
        action.run(suites=["e2e"])
        flags = pytest_flags(runner.last_call())
        assert "-m" in flags
        idx = flags.index("-m")
        assert "e2e" in flags[idx + 1]

    def test_multiple_suites_joined_with_or(self):
        action, runner = make_action()
        action.run(suites=["unit", "integration-mock"])
        flags = pytest_flags(runner.last_call())
        assert "-m" in flags
        idx = flags.index("-m")
        mark = flags[idx + 1]
        assert "unit" in mark
        assert "integration_mock" in mark
        assert " or " in mark

    def test_coverage_adds_cov_flags(self):
        action, runner = make_action()
        action.run(suites=["coverage"])
        cmd = runner.last_call()
        assert any("--cov=" in arg for arg in cmd)
        assert "--cov-report=term-missing" in cmd

    def test_coverage_overrides_other_suites(self):
        action, runner = make_action()
        action.run(suites=["unit", "coverage"])
        flags = pytest_flags(runner.last_call())
        assert any("--cov=" in arg for arg in flags)
        assert "-m" not in flags

    def test_coverage_with_all_suites_still_overrides(self):
        action, runner = make_action()
        action.run(suites=["unit", "integration-mock", "integration-real", "e2e", "coverage"])
        flags = pytest_flags(runner.last_call())
        assert any("--cov=" in arg for arg in flags)
        assert "-m" not in flags

    def test_returns_0_on_success(self):
        runner = FakeSubprocessRunner()
        runner.add_run(returncode=0)
        action = ScriptsTestAction(runner)
        assert action.run() == 0

    def test_returns_nonzero_on_failure(self):
        runner = FakeSubprocessRunner()
        runner.add_run(returncode=1)
        action = ScriptsTestAction(runner)
        assert action.run() == 1

    def test_empty_suites_same_as_none(self):
        action, runner = make_action()
        action.run(suites=[])
        flags = pytest_flags(runner.last_call())
        assert "-m" not in flags
        assert "--cov" not in " ".join(flags)

    def test_coverage_suite_no_marker_flag(self):
        action, runner = make_action()
        action.run(suites=["coverage"])
        flags = pytest_flags(runner.last_call())
        assert "-m" not in flags
