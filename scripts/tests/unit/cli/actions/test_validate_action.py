"""Unit tests for ValidateAction."""

from unittest.mock import MagicMock

import pytest

from cli.actions.validate import ValidateAction

pytestmark = pytest.mark.unit


def _action(tmp_path, run_results=None):
    runner = MagicMock()
    if run_results is not None:
        runner.run.side_effect = [MagicMock(stdout=s, returncode=rc) for s, rc in run_results]
    else:
        runner.run.return_value = MagicMock(stdout="", returncode=0)
    return ValidateAction(runner, project_root=tmp_path)


class TestRun:
    def test_returns_0_when_all_checks_pass(self, tmp_path):
        (tmp_path / "app" / "src").mkdir(parents=True)
        action = _action(tmp_path)
        action._check_branch_name = MagicMock(return_value=True)
        action._check_commit_messages = MagicMock(return_value=True)
        action._check_no_todo = MagicMock(return_value=True)
        action._check_large_files = MagicMock(return_value=True)
        assert action.run() == 0

    def test_returns_1_when_any_check_fails(self, tmp_path):
        action = _action(tmp_path)
        action._check_branch_name = MagicMock(return_value=False)
        action._check_commit_messages = MagicMock(return_value=True)
        action._check_no_todo = MagicMock(return_value=True)
        action._check_large_files = MagicMock(return_value=True)
        assert action.run() == 1

    def test_runs_all_checks_even_when_one_fails(self, tmp_path):
        action = _action(tmp_path)
        action._check_branch_name = MagicMock(return_value=False)
        action._check_commit_messages = MagicMock(return_value=True)
        action._check_no_todo = MagicMock(return_value=True)
        action._check_large_files = MagicMock(return_value=True)
        action.run()
        action._check_commit_messages.assert_called_once()
        action._check_no_todo.assert_called_once()
        action._check_large_files.assert_called_once()


class TestCheckBranchName:
    def test_valid_feature_branch(self, tmp_path):
        action = _action(tmp_path, [("feature/47-Improve_coverage\n", 0)])
        assert action._check_branch_name() is True

    def test_valid_bugfix_branch(self, tmp_path):
        action = _action(tmp_path, [("bugfix/123-fix-thing\n", 0)])
        assert action._check_branch_name() is True

    def test_main_branch_is_invalid(self, tmp_path):
        action = _action(tmp_path, [("main\n", 0)])
        assert action._check_branch_name() is False

    def test_feature_without_issue_number_is_invalid(self, tmp_path):
        action = _action(tmp_path, [("feature/my-feature\n", 0)])
        assert action._check_branch_name() is False


class TestCheckCommitMessages:
    def test_no_commits_returns_true(self, tmp_path):
        action = _action(tmp_path, [("", 0)])
        assert action._check_commit_messages() is True

    def test_valid_commit_returns_true(self, tmp_path):
        action = _action(tmp_path, [("abc123 #47: feat: add feature\n", 0)])
        assert action._check_commit_messages() is True

    def test_invalid_format_returns_false(self, tmp_path):
        action = _action(tmp_path, [("abc123 add feature without prefix\n", 0)])
        assert action._check_commit_messages() is False

    def test_docs_commit_with_kanban_passes(self, tmp_path):
        action = _action(
            tmp_path,
            [
                ("abc123 docs: update notes\n", 0),
                ("kanban.md\n", 0),  # git show files
            ],
        )
        assert action._check_commit_messages() is True

    def test_docs_commit_without_kanban_fails(self, tmp_path):
        action = _action(
            tmp_path,
            [
                ("abc123 docs: update notes\n", 0),
                ("README.md\n", 0),  # git show files — no kanban.md
            ],
        )
        assert action._check_commit_messages() is False


class TestCheckNoTodo:
    def test_no_todos_returns_true(self, tmp_path):
        app_src = tmp_path / "app" / "src"
        app_src.mkdir(parents=True)
        kt_file = app_src / "Clean.kt"
        kt_file.write_text("fun clean() { }\n")
        action = _action(tmp_path)
        assert action._check_no_todo() is True

    def test_todo_comment_returns_false(self, tmp_path):
        app_src = tmp_path / "app" / "src"
        app_src.mkdir(parents=True)
        kt_file = app_src / "Dirty.kt"
        kt_file.write_text("// TODO: fix this\nfun broken() { }\n")
        action = _action(tmp_path)
        assert action._check_no_todo() is False

    def test_fixme_comment_returns_false(self, tmp_path):
        app_src = tmp_path / "app" / "src"
        app_src.mkdir(parents=True)
        kt_file = app_src / "Dirty.kt"
        kt_file.write_text("// FIXME: broken\n")
        action = _action(tmp_path)
        assert action._check_no_todo() is False

    def test_empty_src_returns_true(self, tmp_path):
        (tmp_path / "app" / "src").mkdir(parents=True)
        action = _action(tmp_path)
        assert action._check_no_todo() is True


class TestCheckLargeFiles:
    def test_no_large_files_returns_true(self, tmp_path):
        small_file = tmp_path / "small.txt"
        small_file.write_bytes(b"x" * 100)
        action = _action(tmp_path, [("small.txt\n", 0)])
        assert action._check_large_files() is True

    def test_large_file_returns_false(self, tmp_path):
        big_file = tmp_path / "big.xml"
        big_file.write_bytes(b"x" * (500 * 1024 + 1))
        action = _action(tmp_path, [("big.xml\n", 0)])
        assert action._check_large_files() is False

    def test_large_jar_is_excluded(self, tmp_path):
        big_jar = tmp_path / "big.jar"
        big_jar.write_bytes(b"x" * (600 * 1024))
        action = _action(tmp_path, [("big.jar\n", 0)])
        assert action._check_large_files() is True
