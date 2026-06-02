#!/usr/bin/env python3
"""
Send files to Android phone via ADB with automatic connection.
Calls adb_auto_connect.py first to ensure device is connected.
"""

import json
import subprocess
import sys
from pathlib import Path


def load_test_settings() -> dict:
    """Load test settings from test-settings.json."""
    settings_path = Path(__file__).parent.parent.parent.parent / "app" / "src" / "androidTest" / "assets" / "test-settings.json"
    with open(settings_path, "r", encoding="utf-8") as f:
        return json.load(f)


def ensure_connection(non_interactive: bool = False) -> bool:
    """
    Ensure ADB connection using adb_auto_connect.py.
    If pairing needed, asks user interactively (unless non_interactive=True).

    Returns:
        True if connected successfully
    """
    script_dir = Path(__file__).parent
    connect_script = script_dir / "adb_connect.py"

    print("🔍 Checking ADB connection...")
    sys.stdout.flush()

    # First attempt: auto-connect (saved device or discovered)
    try:
        result = subprocess.run(
            [sys.executable, str(connect_script)],
            capture_output=False,  # Let output show directly for interactive prompts
            timeout=60,
        )

        if result.returncode == 0:
            print("✅ Device connected")
            return True

    except subprocess.TimeoutExpired:
        print("❌ Connection timed out")
        return False
    except Exception as e:
        print(f"❌ Connection error: {e}")
        return False

    # In non-interactive mode (e.g., called from Gradle), don't prompt
    if non_interactive:
        print("❌ Device not connected. Run manually to pair: python send_to_phone.py")
        return False

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
                str(connect_script),
                "--pair",
                pairing_code,
                "--pair-address",
                pairing_address,
            ],
            capture_output=False,
            timeout=60,
        )

        if result.returncode == 0:
            print("✅ Pairing and connection successful")
            return True
        else:
            print("❌ Pairing failed")
            return False

    except (KeyboardInterrupt, EOFError):
        print("\n❌ Cancelled")
        return False
    except Exception as e:
        print(f"❌ Pairing error: {e}")
        return False


def push_file(local_path: str, remote_path: str) -> bool:
    """
    Push file to Android device.

    Args:
        local_path: Path to local file
        remote_path: Destination path on device

    Returns:
        True if push successful
    """
    local_file = Path(local_path)

    if not local_file.exists():
        print(f"❌ File not found: {local_path}")
        return False

    # Build remote file path
    remote_file = f"{remote_path}/{local_file.name}"

    # Check if file already exists on device
    print(f"\n🔍 Checking: {local_file.name}")
    check_result = subprocess.run(
        ["adb", "shell", f"test -f {remote_file} && echo exists || echo missing"],
        capture_output=True,
        text=True,
        timeout=10,
    )

    if "exists" in check_result.stdout:
        print(f"⏭️  Already exists on device, skipping")
        return True

    # Get file size for progress
    file_size_mb = local_file.stat().st_size / (1024 * 1024)

    print(f"📤 Pushing: {local_file.name} ({file_size_mb:.2f} MB)")
    print(f"   Destination: {remote_path}")

    # Ensure destination directory exists first
    mkdir_result = subprocess.run(
        ["adb", "shell", f"mkdir -p {remote_path}"],
        capture_output=True,
        text=True,
        timeout=10,
    )

    if mkdir_result.returncode != 0:
        print(f"❌ Failed to create directory: {remote_path}")
        print(mkdir_result.stderr)
        return False

    try:
        with subprocess.Popen(
            ["adb", "push", str(local_file), remote_path],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        ) as proc:
            for line in proc.stdout:
                line = line.strip()
                if line:
                    print(f"   {line}", end="\r", flush=True)
            proc.wait()

        print()
        if proc.returncode == 0:
            print(f"✅ Transferred")
            return True
        else:
            print(f"❌ Transfer failed")
            return False

    except Exception as e:
        print(f"❌ Transfer error: {e}")
        return False


def main():
    """Main entry point."""
    import argparse
    import io

    # Fix Windows console encoding
    if sys.platform == "win32":
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8")

    # Load test settings for default destination
    test_settings = load_test_settings()
    default_dest = test_settings["test_archives"]["device_path"]

    parser = argparse.ArgumentParser(
        description="Send files to Android phone via ADB with automatic connection"
    )
    parser.add_argument(
        "files",
        nargs="*",  # Changed to nargs="*" to allow zero files
        help="File(s) to send to phone (if empty, sends all test archives from test-settings.json)",
    )
    parser.add_argument(
        "--dest",
        default=default_dest,
        help=f"Destination path on device (default: {default_dest})",
    )
    parser.add_argument(
        "--ci",
        action="store_true",
        help="Non-interactive mode (used when called from Gradle). Fails if device not already connected.",
    )

    args = parser.parse_args()

    # If no files specified, use test archives from JSON
    if not args.files:
        project_root = Path(__file__).parent.parent.parent.parent
        host_path = test_settings["test_archives"]["host_path"]
        archive_files = test_settings["test_archives"]["files"]

        args.files = [
            str(project_root / host_path / filename)
            for filename in archive_files
        ]
        print(f"📦 Sending {len(args.files)} test archives from configuration...")

    # Ensure connection first
    if not ensure_connection(non_interactive=args.ci):
        print("\n💡 Make sure 'Wireless debugging' is enabled on your device")
        print("   If first time, run: python adb_connect.py --pair CODE --pair-address IP:PORT")
        sys.exit(1)

    # Push all files
    success_count = 0
    fail_count = 0

    for file_path in args.files:
        if push_file(file_path, args.dest):
            success_count += 1
        else:
            fail_count += 1

    # Summary
    print(f"\n{'='*60}")
    print(f"✅ Transferred: {success_count}")
    if fail_count > 0:
        print(f"❌ Failed: {fail_count}")
    print(f"{'='*60}")

    sys.exit(0 if fail_count == 0 else 1)


if __name__ == "__main__":
    main()
