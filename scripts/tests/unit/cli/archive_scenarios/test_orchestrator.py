#!/usr/bin/env python3
"""Unit tests for the fixture-scenario orchestrator — verifies wiring, not real I/O."""

from unittest.mock import MagicMock, patch

from fake_subprocess import FakeSubprocessRunner

from cli.archive_scenarios.orchestrator import create_all_fixture_archives


def _mock_scenario(return_value=None):
    instance = MagicMock()
    instance.create_all.return_value = return_value or {}
    return instance


class TestCreateAllFixtureArchives:
    def test_calls_perfect_archives(self, tmp_path):
        runner = FakeSubprocessRunner()
        with (
            patch("cli.archive_scenarios.orchestrator.PerfectArchives") as MockPerfect,
            patch("cli.archive_scenarios.orchestrator.CorruptedArchives") as MockCorrupted,
            patch("cli.archive_scenarios.orchestrator.EmptyArchives") as MockEmpty,
            patch("cli.archive_scenarios.orchestrator.MaliciousArchives") as MockMalicious,
            patch("cli.archive_scenarios.orchestrator.LargeArchives") as MockLarge,
            patch("cli.archive_scenarios.orchestrator.DeepNestedArchives") as MockDeepNested,
            patch("cli.archive_scenarios.orchestrator.LongFilenameArchives") as MockLongFilename,
        ):
            MockPerfect.return_value = _mock_scenario({"zip": tmp_path / "test_archive.zip"})
            MockCorrupted.return_value = _mock_scenario()
            MockEmpty.return_value = _mock_scenario()
            MockMalicious.return_value = _mock_scenario()
            MockLarge.return_value = _mock_scenario()
            MockDeepNested.return_value = _mock_scenario()
            MockLongFilename.return_value = _mock_scenario()

            create_all_fixture_archives(runner, tmp_path / "out", tmp_path / "template")

            MockPerfect.assert_called_once_with(runner, tmp_path / "out", tmp_path / "template")
            MockPerfect.return_value.create_all.assert_called_once()

    def test_corrupted_receives_perfect_results(self, tmp_path):
        runner = FakeSubprocessRunner()
        perfect_output = {"zip": tmp_path / "test_archive.zip"}
        with (
            patch("cli.archive_scenarios.orchestrator.PerfectArchives") as MockPerfect,
            patch("cli.archive_scenarios.orchestrator.CorruptedArchives") as MockCorrupted,
            patch("cli.archive_scenarios.orchestrator.EmptyArchives") as MockEmpty,
            patch("cli.archive_scenarios.orchestrator.MaliciousArchives") as MockMalicious,
            patch("cli.archive_scenarios.orchestrator.LargeArchives") as MockLarge,
            patch("cli.archive_scenarios.orchestrator.DeepNestedArchives") as MockDeepNested,
            patch("cli.archive_scenarios.orchestrator.LongFilenameArchives") as MockLongFilename,
        ):
            MockPerfect.return_value = _mock_scenario(perfect_output)
            for mock_cls in (MockCorrupted, MockEmpty, MockMalicious, MockLarge, MockDeepNested, MockLongFilename):
                mock_cls.return_value = _mock_scenario()

            create_all_fixture_archives(runner, tmp_path / "out", tmp_path / "template")

            MockCorrupted.assert_called_once_with(perfect_output, tmp_path / "out")

    def test_stress_scenarios_use_sibling_template_dirs(self, tmp_path):
        runner = FakeSubprocessRunner()
        template = tmp_path / "template"
        with (
            patch("cli.archive_scenarios.orchestrator.PerfectArchives") as MockPerfect,
            patch("cli.archive_scenarios.orchestrator.CorruptedArchives") as MockCorrupted,
            patch("cli.archive_scenarios.orchestrator.EmptyArchives") as MockEmpty,
            patch("cli.archive_scenarios.orchestrator.MaliciousArchives") as MockMalicious,
            patch("cli.archive_scenarios.orchestrator.LargeArchives") as MockLarge,
            patch("cli.archive_scenarios.orchestrator.DeepNestedArchives") as MockDeepNested,
            patch("cli.archive_scenarios.orchestrator.LongFilenameArchives") as MockLongFilename,
        ):
            MockPerfect.return_value = _mock_scenario({})
            for mock_cls in (MockCorrupted, MockEmpty, MockMalicious, MockLarge, MockDeepNested, MockLongFilename):
                mock_cls.return_value = _mock_scenario()

            create_all_fixture_archives(runner, tmp_path / "out", template)

            empty_dir = MockEmpty.call_args.args[2]
            large_dir = MockLarge.call_args.args[2]
            deep_dir = MockDeepNested.call_args.args[2]
            long_dir = MockLongFilename.call_args.args[2]
            assert {empty_dir, large_dir, deep_dir, long_dir}.isdisjoint({template})
            assert all(d.parent == template.parent for d in (empty_dir, large_dir, deep_dir, long_dir))

    def test_malicious_receives_output_dir_only(self, tmp_path):
        runner = FakeSubprocessRunner()
        with (
            patch("cli.archive_scenarios.orchestrator.PerfectArchives") as MockPerfect,
            patch("cli.archive_scenarios.orchestrator.CorruptedArchives") as MockCorrupted,
            patch("cli.archive_scenarios.orchestrator.EmptyArchives") as MockEmpty,
            patch("cli.archive_scenarios.orchestrator.MaliciousArchives") as MockMalicious,
            patch("cli.archive_scenarios.orchestrator.LargeArchives") as MockLarge,
            patch("cli.archive_scenarios.orchestrator.DeepNestedArchives") as MockDeepNested,
            patch("cli.archive_scenarios.orchestrator.LongFilenameArchives") as MockLongFilename,
        ):
            MockPerfect.return_value = _mock_scenario({})
            for mock_cls in (MockCorrupted, MockEmpty, MockMalicious, MockLarge, MockDeepNested, MockLongFilename):
                mock_cls.return_value = _mock_scenario()

            create_all_fixture_archives(runner, tmp_path / "out", tmp_path / "template")

            MockMalicious.assert_called_once_with(tmp_path / "out")
