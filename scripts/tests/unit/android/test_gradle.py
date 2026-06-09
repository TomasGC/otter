#!/usr/bin/env python3
"""Unit tests for Gradle utilities."""

import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock, call

# Add src to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent.parent / "src"))

from android.gradle import get_gradle_wrapper, run_gradle_task


class TestGetGradleWrapper(unittest.TestCase):
    """Test get_gradle_wrapper function."""

    @patch('sys.platform', 'win32')
    @patch('android.gradle.get_project_root')
    def test_get_gradle_wrapper_on_windows(self, mock_get_root):
        """Should return gradlew.bat on Windows."""
        mock_get_root.return_value = Path("/project")

        result = get_gradle_wrapper()

        self.assertEqual(result, str(Path("/project/gradlew.bat")))

    @patch('sys.platform', 'linux')
    @patch('android.gradle.get_project_root')
    def test_get_gradle_wrapper_on_linux(self, mock_get_root):
        """Should return gradlew on Linux."""
        mock_get_root.return_value = Path("/project")

        result = get_gradle_wrapper()

        self.assertEqual(result, str(Path("/project/gradlew")))

    @patch('sys.platform', 'darwin')
    @patch('android.gradle.get_project_root')
    def test_get_gradle_wrapper_on_mac(self, mock_get_root):
        """Should return gradlew on macOS."""
        mock_get_root.return_value = Path("/project")

        result = get_gradle_wrapper()

        self.assertEqual(result, str(Path("/project/gradlew")))


class TestRunGradleTask(unittest.TestCase):
    """Test run_gradle_task function."""

    @patch('android.gradle.get_project_root')
    @patch('android.gradle.get_gradle_wrapper')
    @patch('subprocess.Popen')
    @patch('android.gradle.log')
    def test_run_gradle_task_success(
        self, mock_log, mock_popen, mock_get_wrapper, mock_get_root
    ):
        """Should return True when Gradle task succeeds."""
        mock_get_root.return_value = Path("/project")
        mock_get_wrapper.return_value = "/project/gradlew"

        # Mock Popen process
        mock_process = MagicMock()
        mock_process.stdout = ["Line 1\n", "Line 2\n", "BUILD SUCCESSFUL\n"]
        mock_process.returncode = 0
        mock_process.wait.return_value = None
        mock_popen.return_value = mock_process

        result = run_gradle_task("assembleDebug")

        self.assertTrue(result)
        mock_popen.assert_called_once_with(
            ["/project/gradlew", "assembleDebug"],
            cwd=Path("/project"),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1
        )

    @patch('android.gradle.get_project_root')
    @patch('android.gradle.get_gradle_wrapper')
    @patch('subprocess.Popen')
    @patch('android.gradle.log')
    def test_run_gradle_task_failure(
        self, mock_log, mock_popen, mock_get_wrapper, mock_get_root
    ):
        """Should return False when Gradle task fails."""
        mock_get_root.return_value = Path("/project")
        mock_get_wrapper.return_value = "/project/gradlew"

        # Mock failed process
        mock_process = MagicMock()
        mock_process.stdout = ["Line 1\n", "BUILD FAILED\n"]
        mock_process.returncode = 1
        mock_process.wait.return_value = None
        mock_popen.return_value = mock_process

        result = run_gradle_task("assembleDebug")

        self.assertFalse(result)
        # Verify error message logged
        error_calls = [call for call in mock_log.call_args_list
                      if "failed" in str(call).lower()]
        self.assertTrue(len(error_calls) > 0)

    @patch('android.gradle.get_project_root')
    @patch('android.gradle.get_gradle_wrapper')
    @patch('subprocess.Popen')
    @patch('android.gradle.log')
    def test_run_gradle_task_timeout(
        self, mock_log, mock_popen, mock_get_wrapper, mock_get_root
    ):
        """Should return False when Gradle task times out."""
        mock_get_root.return_value = Path("/project")
        mock_get_wrapper.return_value = "/project/gradlew"

        # Mock timeout
        mock_process = MagicMock()
        mock_process.stdout = ["Line 1\n"]
        mock_process.wait.side_effect = subprocess.TimeoutExpired("gradlew", 600)
        mock_popen.return_value = mock_process

        result = run_gradle_task("assembleDebug", timeout=600)

        self.assertFalse(result)
        mock_process.kill.assert_called_once()
        # Verify timeout message logged
        timeout_calls = [call for call in mock_log.call_args_list
                        if "timed out" in str(call).lower()]
        self.assertTrue(len(timeout_calls) > 0)

    @patch('android.gradle.get_project_root')
    @patch('android.gradle.get_gradle_wrapper')
    @patch('subprocess.Popen')
    @patch('android.gradle.log')
    def test_run_gradle_task_streams_output(
        self, mock_log, mock_popen, mock_get_wrapper, mock_get_root
    ):
        """Should stream Gradle output line by line."""
        mock_get_root.return_value = Path("/project")
        mock_get_wrapper.return_value = "/project/gradlew"

        lines = ["Line 1\n", "Line 2\n", "Line 3\n"]
        mock_process = MagicMock()
        mock_process.stdout = lines
        mock_process.returncode = 0
        mock_process.wait.return_value = None
        mock_popen.return_value = mock_process

        result = run_gradle_task("assembleDebug")

        # Verify each line was logged
        self.assertTrue(result)
        self.assertEqual(mock_log.call_count, len(lines))

    @patch('android.gradle.get_project_root')
    @patch('android.gradle.get_gradle_wrapper')
    @patch('subprocess.Popen')
    @patch('android.gradle.log')
    def test_run_gradle_task_with_custom_timeout(
        self, mock_log, mock_popen, mock_get_wrapper, mock_get_root
    ):
        """Should use custom timeout value."""
        mock_get_root.return_value = Path("/project")
        mock_get_wrapper.return_value = "/project/gradlew"

        mock_process = MagicMock()
        mock_process.stdout = ["Line 1\n"]
        mock_process.returncode = 0
        mock_process.wait.return_value = None
        mock_popen.return_value = mock_process

        result = run_gradle_task("assembleDebug", timeout=300)

        self.assertTrue(result)
        mock_process.wait.assert_called_once_with(timeout=300)

    @patch('android.gradle.get_project_root')
    @patch('android.gradle.get_gradle_wrapper')
    @patch('subprocess.Popen')
    @patch('android.gradle.log')
    def test_run_gradle_task_exception_handling(
        self, mock_log, mock_popen, mock_get_wrapper, mock_get_root
    ):
        """Should handle unexpected exceptions."""
        mock_get_root.return_value = Path("/project")
        mock_get_wrapper.return_value = "/project/gradlew"

        mock_popen.side_effect = Exception("Unexpected error")

        result = run_gradle_task("assembleDebug")

        self.assertFalse(result)
        # Verify exception logged
        error_calls = [call for call in mock_log.call_args_list
                      if "failed" in str(call).lower() or "error" in str(call).lower()]
        self.assertTrue(len(error_calls) > 0)


if __name__ == '__main__':
    unittest.main()
