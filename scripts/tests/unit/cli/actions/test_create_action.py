"""Unit tests for CreateAction."""

import sys
from pathlib import Path
from unittest.mock import patch, MagicMock

import pytest

from cli.actions.create import CreateAction, SUITES
from fake_subprocess import FakeSubprocessRunner

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
        with patch("cli.actions.create.CreateAction.run_template") as mock_rt:
            mock_rt.return_value = 0
            rc = action.run_template(output_dir=tmp_path)
        # Direct call via real method — patch ArchiveTemplateGenerator
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
    def test_calls_archive_creator_create_all(self, tmp_path):
        action, runner = make_action(tmp_path)
        mock_creator = MagicMock()
        with patch("cli.create_test_archives.ArchiveCreator") as MockCreator:
            MockCreator.return_value = mock_creator
            action.run_archives(output_dir=tmp_path)
        mock_creator.create_all.assert_called_once_with(rpa_only=False)

    def test_passes_rpa_only_flag(self, tmp_path):
        action, runner = make_action(tmp_path)
        mock_creator = MagicMock()
        with patch("cli.create_test_archives.ArchiveCreator") as MockCreator:
            MockCreator.return_value = mock_creator
            action.run_archives(rpa_only=True, output_dir=tmp_path)
        mock_creator.create_all.assert_called_once_with(rpa_only=True)

    def test_returns_0_on_success(self, tmp_path):
        action, _ = make_action(tmp_path)
        with patch("cli.create_test_archives.ArchiveCreator") as MockCreator:
            MockCreator.return_value = MagicMock()
            rc = action.run_archives(output_dir=tmp_path)
        assert rc == 0

class TestCreateActionRun:
    def test_no_suites_runs_both(self, tmp_path):
        action, _ = make_action(tmp_path)
        with patch.object(action, "run_template", return_value=0) as mt, \
             patch.object(action, "run_archives", return_value=0) as ma:
            rc = action.run()
        mt.assert_called_once()
        ma.assert_called_once()
        assert rc == 0

    def test_template_suite_runs_only_template(self, tmp_path):
        action, _ = make_action(tmp_path)
        with patch.object(action, "run_template", return_value=0) as mt, \
             patch.object(action, "run_archives", return_value=0) as ma:
            action.run(suites=["template"])
        mt.assert_called_once()
        ma.assert_not_called()

    def test_archives_suite_runs_only_archives(self, tmp_path):
        action, _ = make_action(tmp_path)
        with patch.object(action, "run_template", return_value=0) as mt, \
             patch.object(action, "run_archives", return_value=0) as ma:
            action.run(suites=["archives"])
        mt.assert_not_called()
        ma.assert_called_once()

    def test_returns_1_when_template_fails(self, tmp_path):
        action, _ = make_action(tmp_path)
        with patch.object(action, "run_template", return_value=1), \
             patch.object(action, "run_archives", return_value=0) as ma:
            rc = action.run()
        assert rc == 1
        ma.assert_not_called()

    def test_returns_1_when_archives_fails(self, tmp_path):
        action, _ = make_action(tmp_path)
        with patch.object(action, "run_template", return_value=0), \
             patch.object(action, "run_archives", return_value=1):
            rc = action.run()
        assert rc == 1

    def test_rpa_only_passed_to_run_archives(self, tmp_path):
        action, _ = make_action(tmp_path)
        with patch.object(action, "run_template", return_value=0), \
             patch.object(action, "run_archives", return_value=0) as ma:
            action.run(suites=["archives"], rpa_only=True)
        ma.assert_called_once_with(rpa_only=True, output_dir=None)

    def test_output_dir_passed_to_run_template(self, tmp_path):
        action, _ = make_action()
        with patch.object(action, "run_template", return_value=0) as mt, \
             patch.object(action, "run_archives", return_value=0):
            action.run(suites=["template"], output_dir=tmp_path)
        mt.assert_called_once_with(tmp_path)

    def test_empty_suites_same_as_no_suites(self, tmp_path):
        action, _ = make_action(tmp_path)
        with patch.object(action, "run_template", return_value=0) as mt, \
             patch.object(action, "run_archives", return_value=0) as ma:
            action.run(suites=[])
        mt.assert_called_once()
        ma.assert_called_once()
