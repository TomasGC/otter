"""Unit tests for CreateAction."""

from unittest.mock import MagicMock, patch

import pytest
from fake_subprocess import FakeSubprocessRunner

from cli.actions.create import SUITES, CreateAction

pytestmark = pytest.mark.unit


def make_action(tmp_path=None):
    runner = FakeSubprocessRunner()
    return CreateAction(runner, output_dir=tmp_path), runner


class TestSuitesConstant:
    def test_contains_expected_suites(self):
        assert "template" in SUITES
        assert "archives" in SUITES


class TestRunTemplate:
    def test_calls_generator_generate(self, tmp_path):
        action, _ = make_action(tmp_path)
        mock_gen = MagicMock()
        with patch("cli.generate_archive_template.ArchiveTemplateGenerator") as MockGen:
            MockGen.return_value = mock_gen
            rc = action.run_template(output_dir=tmp_path)
        mock_gen.generate.assert_called_once_with(tmp_path)
        assert rc == 0

    def test_returns_0_on_success(self, tmp_path):
        action, _ = make_action(tmp_path)
        with patch("cli.generate_archive_template.ArchiveTemplateGenerator") as MockGen:
            MockGen.return_value = MagicMock()
            rc = action.run_template(output_dir=tmp_path)
        assert rc == 0


class TestRunArchives:
    def test_calls_orchestrator_with_runner_and_output_dir(self, tmp_path):
        action, runner = make_action(tmp_path)
        with patch("cli.archive_scenarios.orchestrator.create_all_fixture_archives") as mock_create_all:
            action.run_archives(output_dir=tmp_path)
        mock_create_all.assert_called_once()
        call_runner, call_out, call_template = mock_create_all.call_args.args
        assert call_runner is runner
        assert call_out == tmp_path
        assert call_template.name == "template"

    def test_uses_injected_template_dir(self, tmp_path):
        template_dir = tmp_path / "custom_template"
        runner = FakeSubprocessRunner()
        action = CreateAction(runner, output_dir=tmp_path, template_dir=template_dir)
        with patch("cli.archive_scenarios.orchestrator.create_all_fixture_archives") as mock_create_all:
            action.run_archives(output_dir=tmp_path)
        assert mock_create_all.call_args.args[2] == template_dir

    def test_returns_0_on_success(self, tmp_path):
        action, _ = make_action(tmp_path)
        with patch("cli.archive_scenarios.orchestrator.create_all_fixture_archives"):
            rc = action.run_archives(output_dir=tmp_path)
        assert rc == 0

    def test_falls_back_to_test_settings_host_path_when_no_output_dir(self, tmp_path):
        runner = FakeSubprocessRunner()
        action = CreateAction(runner)  # no output_dir, no template_dir injected
        fake_settings = {"test_archives": {"host_path": "archives"}}
        with (
            patch("cli.archive_scenarios.perfect.PROJECT_ROOT", tmp_path),
            patch("common.file_utils.load_test_settings", return_value=fake_settings),
            patch("cli.archive_scenarios.orchestrator.create_all_fixture_archives") as mock_create_all,
        ):
            action.run_archives()
        assert mock_create_all.call_args.args[1] == tmp_path / "archives"


class TestCreateActionRun:
    def test_no_suites_runs_both(self, tmp_path):
        action, _ = make_action(tmp_path)
        with (
            patch.object(action, "run_template", return_value=0) as mt,
            patch.object(action, "run_archives", return_value=0) as ma,
        ):
            rc = action.run()
        mt.assert_called_once()
        ma.assert_called_once()
        assert rc == 0

    def test_template_suite_runs_only_template(self, tmp_path):
        action, _ = make_action(tmp_path)
        with (
            patch.object(action, "run_template", return_value=0) as mt,
            patch.object(action, "run_archives", return_value=0) as ma,
        ):
            action.run(suites=["template"])
        mt.assert_called_once()
        ma.assert_not_called()

    def test_archives_suite_runs_only_archives(self, tmp_path):
        action, _ = make_action(tmp_path)
        with (
            patch.object(action, "run_template", return_value=0) as mt,
            patch.object(action, "run_archives", return_value=0) as ma,
        ):
            action.run(suites=["archives"])
        mt.assert_not_called()
        ma.assert_called_once()

    def test_returns_1_when_template_fails(self, tmp_path):
        action, _ = make_action(tmp_path)
        with (
            patch.object(action, "run_template", return_value=1),
            patch.object(action, "run_archives", return_value=0) as ma,
        ):
            rc = action.run()
        assert rc == 1
        ma.assert_not_called()

    def test_returns_1_when_archives_fails(self, tmp_path):
        action, _ = make_action(tmp_path)
        with patch.object(action, "run_template", return_value=0), patch.object(action, "run_archives", return_value=1):
            rc = action.run()
        assert rc == 1

    def test_output_dir_passed_to_run_template(self, tmp_path):
        action, _ = make_action()
        with (
            patch.object(action, "run_template", return_value=0) as mt,
            patch.object(action, "run_archives", return_value=0),
        ):
            action.run(suites=["template"], output_dir=tmp_path)
        mt.assert_called_once_with(tmp_path)

    def test_empty_suites_same_as_no_suites(self, tmp_path):
        action, _ = make_action(tmp_path)
        with (
            patch.object(action, "run_template", return_value=0) as mt,
            patch.object(action, "run_archives", return_value=0) as ma,
        ):
            action.run(suites=[])
        mt.assert_called_once()
        ma.assert_called_once()
