#!/usr/bin/env python3
"""Unit tests for ADB utilities."""

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock, call

# Add src to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent.parent / "src"))

from android.adb import (
    is_adb_available,
    get_connected_devices,
    install_apk,
    auto_connect_device
)


class TestIsAdbAvailable(unittest.TestCase):
    """Test is_adb_available function."""

    @patch('subprocess.run')
    def test_is_adb_available_when_installed(self, mock_run):
        """Should return True when adb command exists."""
        mock_run.return_value = MagicMock(returncode=0)

        result = is_adb_available()

        self.assertTrue(result)
        mock_run.assert_called_once_with(
            ["adb", "version"],
            capture_output=True,
            timeout=3,
            check=True
        )

    @patch('subprocess.run')
    def test_is_adb_available_when_not_installed(self, mock_run):
        """Should return False when adb command not found."""
        mock_run.side_effect = FileNotFoundError()

        result = is_adb_available()

        self.assertFalse(result)

    @patch('subprocess.run')
    def test_is_adb_available_when_command_fails(self, mock_run):
        """Should return False when adb command fails."""
        mock_run.side_effect = subprocess.CalledProcessError(1, "adb")

        result = is_adb_available()

        self.assertFalse(result)

    @patch('subprocess.run')
    def test_is_adb_available_when_timeout(self, mock_run):
        """Should return False when adb command times out."""
        mock_run.side_effect = subprocess.TimeoutExpired("adb", 3)

        result = is_adb_available()

        self.assertFalse(result)


class TestGetConnectedDevices(unittest.TestCase):
    """Test get_connected_devices function."""

    @patch('android.adb.is_adb_available')
    def test_get_connected_devices_when_adb_not_available(self, mock_is_available):
        """Should return empty list when adb not available."""
        mock_is_available.return_value = False

        result = get_connected_devices()

        self.assertEqual(result, [])

    @patch('android.adb.is_adb_available')
    @patch('subprocess.run')
    def test_get_connected_devices_with_one_device(self, mock_run, mock_is_available):
        """Should return list with one device."""
        mock_is_available.return_value = True
        mock_run.return_value = MagicMock(
            returncode=0,
            stdout="List of devices attached\nTEST_DEVICE_123\tdevice\n"
        )

        result = get_connected_devices()

        self.assertEqual(result, ["TEST_DEVICE_123"])

    @patch('android.adb.is_adb_available')
    @patch('subprocess.run')
    def test_get_connected_devices_with_multiple_devices(self, mock_run, mock_is_available):
        """Should return list with multiple devices."""
        mock_is_available.return_value = True
        mock_run.return_value = MagicMock(
            returncode=0,
            stdout="List of devices attached\nTEST_DEVICE_123\tdevice\nemulator-5554\tdevice\n"
        )

        result = get_connected_devices()

        self.assertEqual(result, ["TEST_DEVICE_123", "emulator-5554"])

    @patch('android.adb.is_adb_available')
    @patch('subprocess.run')
    def test_get_connected_devices_with_wifi_device(self, mock_run, mock_is_available):
        """Should handle WiFi device with mDNS service name."""
        mock_is_available.return_value = True
        mock_run.return_value = MagicMock(
            returncode=0,
            stdout="List of devices attached\nadb-TEST_DEVICE_123-XyZ123._adb-tls-connect._tcp\tdevice\n"
        )

        result = get_connected_devices()

        self.assertEqual(result, ["adb-TEST_DEVICE_123-XyZ123._adb-tls-connect._tcp"])

    @patch('android.adb.is_adb_available')
    @patch('subprocess.run')
    def test_get_connected_devices_with_no_devices(self, mock_run, mock_is_available):
        """Should return empty list when no devices connected."""
        mock_is_available.return_value = True
        mock_run.return_value = MagicMock(
            returncode=0,
            stdout="List of devices attached\n"
        )

        result = get_connected_devices()

        self.assertEqual(result, [])

    @patch('android.adb.is_adb_available')
    @patch('subprocess.run')
    def test_get_connected_devices_when_command_fails(self, mock_run, mock_is_available):
        """Should return empty list when adb devices command fails."""
        mock_is_available.return_value = True
        mock_run.side_effect = subprocess.CalledProcessError(1, "adb")

        result = get_connected_devices()

        self.assertEqual(result, [])

    @patch('android.adb.is_adb_available')
    @patch('subprocess.run')
    def test_get_connected_devices_when_timeout(self, mock_run, mock_is_available):
        """Should return empty list when command times out."""
        mock_is_available.return_value = True
        mock_run.side_effect = subprocess.TimeoutExpired("adb", 3)

        result = get_connected_devices()

        self.assertEqual(result, [])


class TestInstallApk(unittest.TestCase):
    """Test install_apk function."""

    @patch('builtins.print')
    def test_install_apk_when_file_not_exists(self, mock_print):
        """Should return False when APK file doesn't exist."""
        apk_path = Path("/nonexistent/app.apk")

        result = install_apk(apk_path)

        self.assertFalse(result)
        mock_print.assert_called_once()
        self.assertIn("not found", mock_print.call_args[0][0])

    @patch('subprocess.run')
    def test_install_apk_success(self, mock_run):
        """Should return True when installation succeeds."""
        with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as tmp:
            apk_path_str = tmp.name

        apk_path = Path(apk_path_str)
        try:
            mock_run.return_value = MagicMock(
                returncode=0,
                stdout="Success"
            )

            result = install_apk(apk_path)

            self.assertTrue(result)
            mock_run.assert_called_once()
            self.assertEqual(mock_run.call_args[0][0][:3], ["adb", "install", "-r"])
        finally:
            apk_path.unlink(missing_ok=True)

    @patch('subprocess.run')
    def test_install_apk_with_device_specified(self, mock_run):
        """Should use -s flag when device specified."""
        with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as tmp:
            apk_path_str = tmp.name

        apk_path = Path(apk_path_str)
        try:
            mock_run.return_value = MagicMock(
                returncode=0,
                stdout="Success"
            )

            result = install_apk(apk_path, device="TEST_DEVICE_123")

            self.assertTrue(result)
            called_cmd = mock_run.call_args[0][0]
            self.assertIn("-s", called_cmd)
            self.assertIn("TEST_DEVICE_123", called_cmd)
        finally:
            apk_path.unlink(missing_ok=True)

    @patch('subprocess.run')
    @patch('builtins.print')
    def test_install_apk_when_command_fails(self, mock_print, mock_run):
        """Should return False when adb install fails."""
        with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as tmp:
            apk_path_str = tmp.name

        apk_path = Path(apk_path_str)
        try:
            mock_run.side_effect = subprocess.CalledProcessError(
                1, "adb", stderr="error"
            )

            result = install_apk(apk_path)

            self.assertFalse(result)
            mock_print.assert_called()
            self.assertIn("failed", mock_print.call_args[0][0])
        finally:
            apk_path.unlink(missing_ok=True)

    @patch('subprocess.run')
    @patch('builtins.print')
    def test_install_apk_when_timeout(self, mock_print, mock_run):
        """Should return False when installation times out."""
        with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as tmp:
            apk_path_str = tmp.name

        apk_path = Path(apk_path_str)
        try:
            mock_run.side_effect = subprocess.TimeoutExpired("adb", 60)

            result = install_apk(apk_path)

            self.assertFalse(result)
            mock_print.assert_called()
            self.assertIn("timed out", mock_print.call_args[0][0])
        finally:
            apk_path.unlink(missing_ok=True)

    @patch('subprocess.run')
    @patch('builtins.print')
    def test_install_apk_when_no_success_in_output(self, mock_print, mock_run):
        """Should return False when Success not in output."""
        with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as tmp:
            apk_path_str = tmp.name

        apk_path = Path(apk_path_str)
        try:
            mock_run.return_value = MagicMock(
                returncode=0,
                stdout="Failed"
            )

            result = install_apk(apk_path)

            self.assertFalse(result)
            mock_print.assert_called()
            self.assertIn("failed", mock_print.call_args[0][0])
        finally:
            apk_path.unlink(missing_ok=True)


class TestAutoConnectDevice(unittest.TestCase):
    """Test auto_connect_device function."""

    @patch('android.adb.get_connected_devices')
    @patch('subprocess.run')
    @patch('builtins.print')
    def test_auto_connect_device_when_already_connected(
        self, mock_print, mock_run, mock_get_devices
    ):
        """Should return device ID when auto-connect succeeds."""
        mock_run.return_value = MagicMock(returncode=0)
        mock_get_devices.return_value = ["TEST_DEVICE_123"]

        result = auto_connect_device()

        self.assertEqual(result, "TEST_DEVICE_123")

    @patch('android.adb.get_connected_devices')
    @patch('subprocess.run')
    @patch('builtins.print')
    def test_auto_connect_device_when_script_fails(
        self, mock_print, mock_run, mock_get_devices
    ):
        """Should return None when connection fails."""
        mock_run.return_value = MagicMock(returncode=1)
        mock_get_devices.return_value = []

        # Mock input to avoid blocking
        with patch('builtins.input', side_effect=KeyboardInterrupt):
            result = auto_connect_device()

        self.assertIsNone(result)

    @patch('android.adb.get_connected_devices')
    @patch('subprocess.run')
    @patch('builtins.input')
    @patch('builtins.print')
    def test_auto_connect_device_with_pairing(
        self, mock_print, mock_input, mock_run, mock_get_devices
    ):
        """Should handle pairing when not connected."""
        # First call (auto-connect) fails with return code 1
        # Second call (pairing) succeeds with return code 0
        mock_run.side_effect = [
            MagicMock(returncode=1),  # Auto-connect fails
            MagicMock(returncode=0),   # Pairing succeeds
        ]
        # First get_devices (after auto-connect fails) returns empty
        # Second get_devices (after pairing succeeds) returns device
        mock_get_devices.side_effect = [["TEST_DEVICE_123"]]  # Only called once after pairing
        mock_input.side_effect = ["123456", "192.168.1.146:37445"]

        result = auto_connect_device()

        self.assertEqual(result, "TEST_DEVICE_123")

    @patch('subprocess.run')
    @patch('builtins.print')
    def test_auto_connect_device_when_timeout(self, mock_print, mock_run):
        """Should return None when connection times out."""
        mock_run.side_effect = subprocess.TimeoutExpired("python", 60)

        result = auto_connect_device()

        self.assertIsNone(result)


if __name__ == '__main__':
    unittest.main()
