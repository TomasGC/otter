#!/usr/bin/env python3
"""ADB device management utilities."""

import subprocess
import sys
from pathlib import Path

# Import from common
sys.path.insert(0, str(Path(__file__).parent.parent))
from common.file_utils import get_project_root


def is_adb_available() -> bool:
    """Check if adb command is available."""
    try:
        subprocess.run(
            ["adb", "version"],
            capture_output=True,
            timeout=3,
            check=True
        )
        return True
    except (subprocess.CalledProcessError, FileNotFoundError, subprocess.TimeoutExpired):
        return False


def get_connected_devices() -> list[str]:
    """
    Get list of connected ADB devices.

    Returns:
        List of device serial numbers (includes full mDNS service name for WiFi devices)
    """
    if not is_adb_available():
        return []

    try:
        result = subprocess.run(
            ["adb", "devices"],
            capture_output=True,
            text=True,
            timeout=3,
            check=True
        )

        devices = []
        for line in result.stdout.split("\n"):
            if "device" in line and "List of devices" not in line:
                # Extract device ID - may include mDNS service name
                # Format: "adb-ABCD1234EFG-XyZ123 (2)._adb-tls-connect._tcp	device"
                # We need the full ID before the tab character
                parts = line.split("\t")
                if parts:
                    device_id = parts[0].strip()
                    if device_id:
                        devices.append(device_id)

        return devices
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return []


def install_apk(apk_path: Path, device: str | None = None) -> bool:
    """
    Install APK on device.

    Args:
        apk_path: Path to APK file
        device: Optional device serial (if None, uses first connected)

    Returns:
        True if successful, False otherwise
    """
    if not apk_path.exists():
        print(f"❌ APK not found: {apk_path}")
        return False

    cmd = ["adb"]
    if device:
        cmd.extend(["-s", device])
    cmd.extend(["install", "-r", str(apk_path)])

    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=60,
            check=True
        )

        if "Success" in result.stdout:
            return True
        else:
            print(f"❌ Install failed: {result.stdout}")
            return False
    except subprocess.CalledProcessError as e:
        print(f"❌ Install failed: {e.stderr}")
        return False
    except subprocess.TimeoutExpired:
        print("❌ Install timed out")
        return False


def auto_connect_device() -> str | None:
    """
    Automatically connect to device, handling pairing interactively if needed.

    Returns:
        Device serial/connection string if successful, None otherwise
    """
    print("🔍 Checking ADB connection...")

    cli_path = str(Path(__file__).parent.parent / "cli")
    if cli_path not in sys.path:
        sys.path.insert(0, cli_path)

    from adb_connect import auto_connect
    return auto_connect()
