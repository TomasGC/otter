#!/usr/bin/env python3
"""
Build and install Otter Android app.

Features:
- Increments versionCode and versionName automatically
- Builds debug APK
- Auto-installs on connected device (with auto-connect if needed)
- Cross-platform (Windows/Linux/Mac)

Usage:
    python scripts/cli/build.py              # Build + install if device connected
    python scripts/cli/build.py --no-install # Build only (no install)
"""

import argparse
import sys
from pathlib import Path

# Add src to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from common import (
    setup_windows_encoding,
    log,
    print_header,
    get_project_root,
    setup_log_file,
    close_log_file,
)
from android import (
    increment_version,
    run_gradle_task,
    get_apk_path,
    get_connected_devices,
    install_apk,
    auto_connect_device,
)


def main():
    """Main build script."""
    setup_windows_encoding()

    parser = argparse.ArgumentParser(description="Build and install Otter Android app")
    parser.add_argument(
        "--no-install",
        action="store_true",
        help="Build only, do not install on device"
    )
    parser.add_argument(
        "--no-log",
        action="store_true",
        help="Disable automatic log file generation"
    )

    args = parser.parse_args()

    # Setup log file (unless --no-log)
    log_path = None
    if not args.no_log:
        project_root = get_project_root()
        temp_dir = project_root / "temp"
        log_path = setup_log_file(temp_dir, prefix="build")
        log(f"📝 Log file: {log_path}")
        log("=" * 80)

    log("🔨 Otter Build Script")

    # Step 1: Increment version
    print_header("Step 1: Incrementing version")

    project_root = get_project_root()
    build_gradle = project_root / "app" / "build.gradle.kts"

    try:
        new_code, new_name = increment_version(build_gradle)
        log(f"✅ Version incremented successfully!")
        log(f"   versionCode: {new_code}")
        log(f"   versionName: {new_name}")
    except Exception as e:
        log(f"❌ Failed to increment version: {e}")
        if log_path:
            close_log_file()
        return 1

    # Step 2: Build APK
    print_header("Step 2: Building APK")

    variant = "debug"
    task = f"assemble{variant.capitalize()}"

    log(f"🔨 Running {task}...")

    if not run_gradle_task(task):
        log(f"❌ Build failed")
        if log_path:
            close_log_file()
        return 1

    apk_path = get_apk_path(variant)
    if not apk_path:
        log(f"❌ APK not found after build")
        if log_path:
            close_log_file()
        return 1

    log(f"✅ APK built successfully!")
    log(f"📦 Location: {apk_path.relative_to(project_root)}")

    # Step 3: Install on device (if not disabled)
    if args.no_install:
        log("\n✅ Build complete (install skipped)")
        return 0

    print_header("Step 3: Installing on device")

    # Check if device already connected
    devices = get_connected_devices()

    if not devices:
        # Auto-connect with pairing support
        device = auto_connect_device()

        if not device:
            log("❌ No device connected and auto-connect failed")
            log("💡 To install manually later:")
            log(f"   adb install -r {apk_path}")
            return 0
    else:
        # Use first device if multiple
        device = devices[0]
        if len(devices) > 1:
            log(f"⚠️  Multiple devices connected, using: {device}")

    log(f"📲 Installing APK on {device}...")

    if install_apk(apk_path, device):
        log(f"✅ App installed successfully!")
        log(f"🚀 You can now launch Otter on your device")

        # Close log file
        if log_path:
            close_log_file()
            print(f"\n📝 Complete log saved to: {log_path}")

        return 0
    else:
        log(f"❌ Installation failed")

        # Close log file
        if log_path:
            close_log_file()
            print(f"\n📝 Complete log saved to: {log_path}")

        return 1


if __name__ == "__main__":
    sys.exit(main())
