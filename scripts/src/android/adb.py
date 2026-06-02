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
    Automatically connect to device using adb_auto_connect.py.
    Handles pairing interactively if needed.

    Returns:
        Device serial/connection string if successful, None otherwise
    """
    script_path = Path(__file__).parent.parent.parent / "adb_auto_connect.py"

    print("🔍 Checking ADB connection...")

    # First attempt: auto-connect (saved device or discovered)
    try:
        result = subprocess.run(
            [sys.executable, str(script_path)],
            capture_output=False,  # Let output show directly for interactive prompts
            timeout=60,
        )

        if result.returncode == 0:
            devices = get_connected_devices()
            if devices:
                print("✅ Device connected")
                return devices[0]

    except subprocess.TimeoutExpired:
        print("❌ Connection timed out")
        return None
    except Exception as e:
        print(f"❌ Connection error: {e}")
        return None

    # If failed, show pairing instructions and ask for info
    print("\n💡 Device not connected. Starting pairing process...")
    print("   1. On your phone: Settings → Developer options → Wireless debugging")
    print("   2. Tap 'Pair device with pairing code'")
    print("   3. Enter the code and IP:PORT shown on your device below:")
    print("")

    try:
        pairing_code = input("Enter 6-digit pairing code: ").strip()
        pairing_address = input("Enter IP:PORT (e.g., 192.168.1.146:37445): ").strip()

        print(f"\n🔐 Pairing with {pairing_address}...")

        # Call connect script with pairing arguments
        result = subprocess.run(
            [
                sys.executable,
                str(script_path),
                "--pair",
                pairing_code,
                "--pair-address",
                pairing_address,
            ],
            capture_output=False,
            timeout=60,
        )

        if result.returncode == 0:
            devices = get_connected_devices()
            if devices:
                print("✅ Pairing and connection successful")
                return devices[0]
            else:
                print("❌ Paired but device not visible")
                return None
        else:
            print("❌ Pairing failed")
            return None

    except (KeyboardInterrupt, EOFError):
        print("\n❌ Cancelled")
        return None
    except Exception as e:
        print(f"❌ Pairing error: {e}")
        return None
