"""Unit tests for LintAction."""

from unittest.mock import MagicMock

import pytest

from cli.actions.lint import LintAction

pytestmark = pytest.mark.unit


def _action(tmp_path, gradle=None):
    runner = MagicMock()
    runner.run.return_value = MagicMock(returncode=0, stdout="")
    g = gradle or MagicMock()
    g.run_task.return_value = True
    return LintAction(runner, project_root=tmp_path, gradle=g), runner, g


class TestRunKotlin:
    def test_returns_0_when_all_pass(self, tmp_path):
        action, _, _ = _action(tmp_path)
        action._kotlin_android_lint = MagicMock(return_value=True)
        action._kotlin_ktlint = MagicMock(return_value=True)
        action._kotlin_detekt = MagicMock(return_value=True)
        action._kotlin_secrets_scan = MagicMock(return_value=None)  # skipped = ok
        assert action.run("kotlin") == 0

    def test_returns_1_when_lint_fails(self, tmp_path):
        action, _, _ = _action(tmp_path)
        action._kotlin_android_lint = MagicMock(return_value=False)
        action._kotlin_ktlint = MagicMock(return_value=True)
        action._kotlin_detekt = MagicMock(return_value=True)
        action._kotlin_secrets_scan = MagicMock(return_value=None)
        assert action.run("kotlin") == 1

    def test_returns_1_when_detekt_fails(self, tmp_path):
        action, _, _ = _action(tmp_path)
        action._kotlin_android_lint = MagicMock(return_value=True)
        action._kotlin_ktlint = MagicMock(return_value=True)
        action._kotlin_detekt = MagicMock(return_value=False)
        action._kotlin_secrets_scan = MagicMock(return_value=None)
        assert action.run("kotlin") == 1

    def test_returns_1_when_secrets_found(self, tmp_path):
        action, _, _ = _action(tmp_path)
        action._kotlin_android_lint = MagicMock(return_value=True)
        action._kotlin_ktlint = MagicMock(return_value=True)
        action._kotlin_detekt = MagicMock(return_value=True)
        action._kotlin_secrets_scan = MagicMock(return_value=False)
        assert action.run("kotlin") == 1


class TestRunPython:
    def test_returns_0_when_all_pass(self, tmp_path):
        action, _, _ = _action(tmp_path)
        action._python_flake8 = MagicMock(return_value=True)
        action._python_style = MagicMock(return_value=True)
        action._python_pylint = MagicMock(return_value=True)
        action._python_mypy = MagicMock(return_value=True)
        action._python_bandit = MagicMock(return_value=True)
        action._python_pip_audit = MagicMock(return_value=True)
        action._python_vulture = MagicMock(return_value=True)
        assert action.run("python") == 0

    def test_returns_1_when_flake8_fails(self, tmp_path):
        action, _, _ = _action(tmp_path)
        action._python_flake8 = MagicMock(return_value=False)
        action._python_style = MagicMock(return_value=True)
        action._python_pylint = MagicMock(return_value=True)
        action._python_mypy = MagicMock(return_value=True)
        action._python_bandit = MagicMock(return_value=True)
        action._python_pip_audit = MagicMock(return_value=True)
        action._python_vulture = MagicMock(return_value=True)
        assert action.run("python") == 1


class TestUnknownTarget:
    def test_returns_1_for_unknown_target(self, tmp_path):
        action, _, _ = _action(tmp_path)
        assert action.run("java") == 1


class TestKotlinAndroidLint:
    def test_calls_gradle_lint_debug(self, tmp_path):
        action, _, gradle = _action(tmp_path)
        action._kotlin_android_lint()
        gradle.run_task.assert_called_once_with("lintDebug")

    def test_returns_gradle_result(self, tmp_path):
        action, _, gradle = _action(tmp_path)
        gradle.run_task.return_value = False
        assert action._kotlin_android_lint() is False


class TestKotlinDetekt:
    def test_calls_gradle_detekt(self, tmp_path):
        action, _, gradle = _action(tmp_path)
        action._kotlin_detekt()
        gradle.run_task.assert_called_once_with("detekt")


class TestKotlinSecretsSkipped:
    def test_skipped_when_trufflehog_absent(self, tmp_path, monkeypatch):
        monkeypatch.setattr("cli.actions.lint.shutil.which", lambda _: None)
        action, _, _ = _action(tmp_path)
        result = action._kotlin_secrets_scan()
        assert result is None


class TestRunDeps:
    def test_returns_0_when_all_pass(self, tmp_path):
        action, _, _ = _action(tmp_path)
        action._kotlin_osv_scan = MagicMock(return_value=True)
        assert action.run("deps") == 0

    def test_returns_0_when_skipped(self, tmp_path):
        action, _, _ = _action(tmp_path)
        action._kotlin_osv_scan = MagicMock(return_value=None)
        assert action.run("deps") == 0

    def test_returns_1_when_osv_fails(self, tmp_path):
        action, _, _ = _action(tmp_path)
        action._kotlin_osv_scan = MagicMock(return_value=False)
        assert action.run("deps") == 1


class TestKotlinOsvScan:
    def test_skipped_when_osv_scanner_absent(self, tmp_path, monkeypatch):
        monkeypatch.setattr("cli.actions.lint.shutil.which", lambda _: None)
        action, _, _ = _action(tmp_path)
        assert action._kotlin_osv_scan() is None

    def test_returns_false_when_metadata_missing(self, tmp_path, monkeypatch):
        monkeypatch.setattr("cli.actions.lint.shutil.which", lambda _: "/usr/bin/osv-scanner")
        action, _, _ = _action(tmp_path)
        assert action._kotlin_osv_scan() is False

    def test_returns_true_when_scan_passes(self, tmp_path, monkeypatch):
        monkeypatch.setattr("cli.actions.lint.shutil.which", lambda _: "/usr/bin/osv-scanner")
        metadata = tmp_path / "gradle" / "verification-metadata.xml"
        metadata.parent.mkdir()
        metadata.write_text("<verification-metadata/>")
        action, runner, _ = _action(tmp_path)
        runner.run.return_value = MagicMock(returncode=0)
        assert action._kotlin_osv_scan() is True

    def test_returns_false_when_scan_finds_vulns(self, tmp_path, monkeypatch):
        monkeypatch.setattr("cli.actions.lint.shutil.which", lambda _: "/usr/bin/osv-scanner")
        metadata = tmp_path / "gradle" / "verification-metadata.xml"
        metadata.parent.mkdir()
        metadata.write_text("<verification-metadata/>")
        action, runner, _ = _action(tmp_path)
        runner.run.return_value = MagicMock(returncode=1)
        assert action._kotlin_osv_scan() is False
