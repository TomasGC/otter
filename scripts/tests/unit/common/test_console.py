#!/usr/bin/env python3
"""Unit tests for console utilities."""

import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from common.console import (
    close_log_file,
    log,
    print_header,
    setup_log_file,
    setup_windows_encoding,
)


class TestSetupWindowsEncoding(unittest.TestCase):
    """Test setup_windows_encoding function."""

    @patch("sys.platform", "win32")
    def test_setup_windows_encoding_on_windows(self):
        """Should setup UTF-8 encoding on Windows."""
        # Save original
        original_stdout = sys.stdout
        original_stderr = sys.stderr

        # Create mock with buffer attribute
        mock_stdout = MagicMock()
        mock_stdout.buffer = io.BytesIO()
        mock_stderr = MagicMock()
        mock_stderr.buffer = io.BytesIO()

        sys.stdout = mock_stdout
        sys.stderr = mock_stderr

        try:
            setup_windows_encoding()

            # Verify stdout/stderr were wrapped
            self.assertIsInstance(sys.stdout, io.TextIOWrapper)
            self.assertIsInstance(sys.stderr, io.TextIOWrapper)
        finally:
            # Restore original
            sys.stdout = original_stdout
            sys.stderr = original_stderr

    @patch("sys.platform", "linux")
    def test_setup_windows_encoding_on_linux(self):
        """Should not modify encoding on Linux."""
        original_stdout = sys.stdout
        original_stderr = sys.stderr

        setup_windows_encoding()

        # Should not change on non-Windows
        self.assertEqual(sys.stdout, original_stdout)
        self.assertEqual(sys.stderr, original_stderr)


class TestSetupLogFile(unittest.TestCase):
    """Test setup_log_file function."""

    def setUp(self):
        """Clean up log file state before each test."""
        import common.console

        common.console._log_file = None

    def tearDown(self):
        """Clean up log file state after each test."""
        close_log_file()

    def test_setup_log_file_creates_directory(self):
        """Should create temp directory if not exists."""
        with tempfile.TemporaryDirectory() as tmpdir:
            temp_path = Path(tmpdir) / "temp"

            log_path = setup_log_file(temp_path, prefix="test")

            try:
                self.assertTrue(temp_path.exists())
                self.assertTrue(log_path.exists())
                self.assertTrue(log_path.name.startswith("test-"))
                self.assertTrue(log_path.name.endswith(".log"))
            finally:
                close_log_file()

    def test_setup_log_file_with_custom_prefix(self):
        """Should use custom prefix for log filename."""
        with tempfile.TemporaryDirectory() as tmpdir:
            temp_path = Path(tmpdir)

            log_path = setup_log_file(temp_path, prefix="build")

            try:
                self.assertTrue(log_path.name.startswith("build-"))
                self.assertTrue(log_path.name.endswith(".log"))
            finally:
                close_log_file()

    def test_setup_log_file_returns_path(self):
        """Should return path to created log file."""
        with tempfile.TemporaryDirectory() as tmpdir:
            temp_path = Path(tmpdir)

            log_path = setup_log_file(temp_path, prefix="test")

            try:
                self.assertIsInstance(log_path, Path)
                self.assertTrue(log_path.exists())
            finally:
                close_log_file()

    def test_setup_log_file_opens_file_for_writing(self):
        """Should open log file in write mode."""
        with tempfile.TemporaryDirectory() as tmpdir:
            temp_path = Path(tmpdir)

            setup_log_file(temp_path, prefix="test")

            try:
                # Should be able to write to log file
                import common.console

                self.assertIsNotNone(common.console._log_file)
                self.assertFalse(common.console._log_file.closed)
            finally:
                close_log_file()


class TestCloseLogFile(unittest.TestCase):
    """Test close_log_file function."""

    def setUp(self):
        """Clean up log file state before each test."""
        import common.console

        common.console._log_file = None

    def tearDown(self):
        """Clean up log file state after each test."""
        close_log_file()

    def test_close_log_file_when_open(self):
        """Should close log file if open."""
        with tempfile.TemporaryDirectory() as tmpdir:
            temp_path = Path(tmpdir)
            setup_log_file(temp_path, prefix="test")

            import common.console

            self.assertIsNotNone(common.console._log_file)

            close_log_file()

            self.assertIsNone(common.console._log_file)

    def test_close_log_file_when_already_closed(self):
        """Should handle closing when no file is open."""
        import common.console

        common.console._log_file = None

        # Should not raise exception
        close_log_file()

        self.assertIsNone(common.console._log_file)


class TestLog(unittest.TestCase):
    """Test log function."""

    def setUp(self):
        """Clean up log file state before each test."""
        import common.console

        common.console._log_file = None

    def tearDown(self):
        """Clean up log file state after each test."""
        close_log_file()

    @patch("builtins.print")
    def test_log_prints_to_console(self, mock_print):
        """Should print message to console."""
        log("Test message")

        mock_print.assert_called_once_with("Test message", end="\n")

    @patch("builtins.print")
    def test_log_with_custom_end(self, mock_print):
        """Should use custom end character."""
        log("Test", end="")

        mock_print.assert_called_once_with("Test", end="")

    def test_log_writes_to_file_when_configured(self):
        """Should write to log file if configured."""
        with tempfile.TemporaryDirectory() as tmpdir:
            temp_path = Path(tmpdir)
            log_path = setup_log_file(temp_path, prefix="test")

            log("Test message")
            log("Another message")

            close_log_file()

            content = log_path.read_text(encoding="utf-8")
            self.assertIn("Test message\n", content)
            self.assertIn("Another message\n", content)

    @patch("builtins.print")
    def test_log_without_file_configured(self, mock_print):
        """Should only print to console when no log file configured."""
        import common.console

        common.console._log_file = None

        log("Test message")

        mock_print.assert_called_once()


class TestPrintHeader(unittest.TestCase):
    """Test print_header function."""

    def setUp(self):
        """Clean up log file state before each test."""
        import common.console

        common.console._log_file = None

    def tearDown(self):
        """Clean up log file state after each test."""
        close_log_file()

    @patch("builtins.print")
    def test_print_header_formats_correctly(self, mock_print):
        """Should print formatted header with separators."""
        print_header("Test Section")

        # Should print 3 lines: newline + separator, title, separator
        self.assertEqual(mock_print.call_count, 3)

    def test_print_header_writes_to_file(self):
        """Should write header to log file if configured."""
        with tempfile.TemporaryDirectory() as tmpdir:
            temp_path = Path(tmpdir)
            log_path = setup_log_file(temp_path, prefix="test")

            print_header("Test Section")

            close_log_file()

            content = log_path.read_text(encoding="utf-8")
            self.assertIn("=" * 60, content)
            self.assertIn("Test Section", content)


if __name__ == "__main__":
    unittest.main()
