"""Unit tests for scripts/pipeline.py — DAG runner."""

from unittest.mock import MagicMock, patch

import pytest

from pipeline import (
    KOTLIN_JOBS,
    PYTHON_JOBS,
    PipelineRunner,
    _build_pipeline,
    _has_python_changes,
    _resolve_jobs,
    main,
)

pytestmark = pytest.mark.unit

ALL_JOBS = KOTLIN_JOBS + PYTHON_JOBS


def _make_runner(jobs: list[dict], ok_map: dict[str, bool] | None = None) -> PipelineRunner:
    """Create a PipelineRunner with _execute mocked."""
    runner = PipelineRunner(jobs)
    runner._execute = MagicMock(side_effect=lambda name, cmd: (ok_map or {}).get(name, True))
    return runner


class TestPipelineRunner:
    def test_all_jobs_run_when_all_pass(self):
        jobs = [
            {"name": "a", "cmd": ["ca"], "needs": []},
            {"name": "b", "cmd": ["cb"], "needs": ["a"]},
        ]
        runner = _make_runner(jobs)
        rc = runner.run()
        assert rc == 0
        assert runner._execute.call_count == 2

    def test_failed_dep_skips_dependent(self):
        jobs = [
            {"name": "a", "cmd": ["ca"], "needs": []},
            {"name": "b", "cmd": ["cb"], "needs": ["a"]},
        ]
        runner = _make_runner(jobs, ok_map={"a": False})
        rc = runner.run()
        assert rc == 1
        assert runner._execute.call_count == 1  # only "a" ran
        assert runner._status["a"] is False
        assert runner._status["b"] is None  # skipped

    def test_independent_chains_both_run_despite_failure(self):
        jobs = [
            {"name": "a", "cmd": ["ca"], "needs": []},
            {"name": "b", "cmd": ["cb"], "needs": []},
            {"name": "c", "cmd": ["cc"], "needs": ["a"]},
        ]
        runner = _make_runner(jobs, ok_map={"a": False, "b": True})
        runner.run()
        # a failed, c skipped, but b still ran
        assert runner._status["a"] is False
        assert runner._status["b"] is True
        assert runner._status["c"] is None

    def test_returns_0_when_all_pass(self):
        jobs = [{"name": "x", "cmd": ["cx"], "needs": []}]
        runner = _make_runner(jobs)
        assert runner.run() == 0

    def test_returns_1_when_any_job_fails(self):
        jobs = [{"name": "x", "cmd": ["cx"], "needs": []}]
        runner = _make_runner(jobs, ok_map={"x": False})
        assert runner.run() == 1

    def test_skipped_jobs_do_not_cause_failure(self):
        jobs = [
            {"name": "a", "cmd": ["ca"], "needs": []},
            {"name": "b", "cmd": ["cb"], "needs": ["a"]},
        ]
        runner = _make_runner(jobs, ok_map={"a": False})
        rc = runner.run()
        assert rc == 1  # a failed (not b skipped)
        assert runner._status["b"] is None  # skip doesn't add to failures

    def test_execute_called_with_correct_args(self):
        jobs = [{"name": "validate", "cmd": ["validate"], "needs": []}]
        runner = _make_runner(jobs)
        runner.run()
        runner._execute.assert_called_once_with("validate", ["validate"])


class TestResolveJobs:
    def test_returns_requested_job(self):
        resolved = _resolve_jobs(["validate"], ALL_JOBS)
        names = [j["name"] for j in resolved]
        assert "validate" in names

    def test_includes_transitive_deps(self):
        resolved = _resolve_jobs(["kotlin-unit-tests"], ALL_JOBS)
        names = [j["name"] for j in resolved]
        assert "kotlin-lint" in names
        assert "kotlin-unit-tests" in names

    def test_does_not_include_unrelated_jobs(self):
        resolved = _resolve_jobs(["kotlin-unit-tests"], ALL_JOBS)
        names = [j["name"] for j in resolved]
        assert "python-lint" not in names
        assert "python-tests" not in names

    def test_preserves_original_pipeline_order(self):
        resolved = _resolve_jobs(["kotlin-unit-tests"], ALL_JOBS)
        names = [j["name"] for j in resolved]
        assert names.index("kotlin-lint") < names.index("kotlin-unit-tests")

    def test_raises_for_unknown_job(self):
        with pytest.raises(ValueError, match="Unknown job"):
            _resolve_jobs(["nonexistent"], ALL_JOBS)

    def test_deep_chain_includes_all_deps(self):
        resolved = _resolve_jobs(["kotlin-coverage"], ALL_JOBS)
        names = [j["name"] for j in resolved]
        assert "kotlin-lint" in names
        assert "kotlin-unit-tests" in names
        assert "kotlin-integ-mocks" in names
        assert "kotlin-integ-reals" in names
        assert "kotlin-build" in names
        assert "kotlin-instrumented" in names
        assert "kotlin-coverage" in names


class TestBuildPipeline:
    def test_kotlin_only_when_no_python(self):
        pipeline = _build_pipeline(include_python=False)
        names = [j["name"] for j in pipeline]
        assert "python-lint" not in names
        assert "python-tests" not in names
        assert "kotlin-lint" in names

    def test_includes_python_jobs_when_flag_set(self):
        pipeline = _build_pipeline(include_python=True)
        names = [j["name"] for j in pipeline]
        assert "python-lint" in names
        assert "python-tests" in names

    def test_kotlin_jobs_always_present(self):
        for flag in [True, False]:
            pipeline = _build_pipeline(include_python=flag)
            names = [j["name"] for j in pipeline]
            assert "kotlin-lint" in names
            assert "kotlin-build" in names

    def test_python_jobs_appended_after_kotlin(self):
        pipeline = _build_pipeline(include_python=True)
        names = [j["name"] for j in pipeline]
        assert names.index("kotlin-coverage") < names.index("python-lint")


class TestHasPythonChanges:
    def test_returns_true_when_py_file_in_branch_diff(self):
        with patch("pipeline.subprocess.run") as mock_run:
            mock_run.side_effect = [
                MagicMock(stdout="scripts/foo.py\n"),
                MagicMock(stdout=""),
                MagicMock(stdout=""),
            ]
            assert _has_python_changes() is True

    def test_returns_false_when_only_kotlin_files(self):
        with patch("pipeline.subprocess.run") as mock_run:
            mock_run.side_effect = [
                MagicMock(stdout="app/src/Foo.kt\n"),
                MagicMock(stdout=""),
                MagicMock(stdout=""),
            ]
            assert _has_python_changes() is False

    def test_returns_true_when_subprocess_raises(self):
        with patch("pipeline.subprocess.run", side_effect=Exception("git fail")):
            assert _has_python_changes() is True

    def test_detects_staged_py_file(self):
        with patch("pipeline.subprocess.run") as mock_run:
            mock_run.side_effect = [
                MagicMock(stdout=""),
                MagicMock(stdout=""),
                MagicMock(stdout="scripts/new.py\n"),
            ]
            assert _has_python_changes() is True

    def test_detects_unstaged_py_file(self):
        with patch("pipeline.subprocess.run") as mock_run:
            mock_run.side_effect = [
                MagicMock(stdout=""),
                MagicMock(stdout="manage.py\n"),
                MagicMock(stdout=""),
            ]
            assert _has_python_changes() is True

    def test_returns_false_when_no_changes(self):
        with patch("pipeline.subprocess.run") as mock_run:
            mock_run.side_effect = [
                MagicMock(stdout=""),
                MagicMock(stdout=""),
                MagicMock(stdout=""),
            ]
            assert _has_python_changes() is False


class TestMain:
    def test_no_args_runs_kotlin_only_when_no_python_changes(self):
        with patch("pipeline._has_python_changes", return_value=False), patch("pipeline.PipelineRunner") as MockRunner:
            MockRunner.return_value.run.return_value = 0
            rc = main([])
        MockRunner.assert_called_once_with(KOTLIN_JOBS)
        assert rc == 0

    def test_no_args_includes_python_when_changes_detected(self):
        with patch("pipeline._has_python_changes", return_value=True), patch("pipeline.PipelineRunner") as MockRunner:
            MockRunner.return_value.run.return_value = 0
            rc = main([])
        MockRunner.assert_called_once_with(KOTLIN_JOBS + PYTHON_JOBS)
        assert rc == 0

    def test_specific_job_filters_pipeline(self):
        with patch("pipeline._has_python_changes", return_value=False), patch("pipeline.PipelineRunner") as MockRunner:
            MockRunner.return_value.run.return_value = 0
            main(["validate"])
        jobs_passed = MockRunner.call_args[0][0]
        assert [j["name"] for j in jobs_passed] == ["validate"]

    def test_unknown_job_returns_1_without_running(self):
        with patch("pipeline._has_python_changes", return_value=False), patch("pipeline.PipelineRunner") as MockRunner:
            rc = main(["nonexistent"])
        MockRunner.assert_not_called()
        assert rc == 1

    def test_python_job_selectable_when_changes_present(self):
        with patch("pipeline._has_python_changes", return_value=True), patch("pipeline.PipelineRunner") as MockRunner:
            MockRunner.return_value.run.return_value = 0
            main(["python-lint"])
        jobs_passed = MockRunner.call_args[0][0]
        assert [j["name"] for j in jobs_passed] == ["python-lint"]

    def test_python_job_unknown_when_no_python_changes(self):
        with patch("pipeline._has_python_changes", return_value=False), patch("pipeline.PipelineRunner") as MockRunner:
            rc = main(["python-lint"])
        MockRunner.assert_not_called()
        assert rc == 1
