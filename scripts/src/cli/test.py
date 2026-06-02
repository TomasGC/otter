#!/usr/bin/env python3
"""
Run tests for Otter Android app.

Features:
- Run unit tests (testDebugUnitTest)
- Run instrumented tests (connectedDebugAndroidTest)
- Generate coverage reports
- Automatic log file generation to temp/
- Cross-platform (Windows/Linux/Mac)

Usage:
    python scripts/cli/test.py                    # Run all tests (logs to temp/)
    python scripts/cli/test.py --unit             # Unit tests only
    python scripts/cli/test.py --instrumented     # Instrumented tests only
    python scripts/cli/test.py --coverage         # Unit tests with coverage

All test output is automatically saved to: temp/test-YYYY-MM-DD_HH-MM-SS.log
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path

# Add src to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from common import (
    setup_windows_encoding,
    setup_log_file,
    close_log_file,
    log,
    print_header,
    get_project_root,
)
from android import run_gradle_task, get_connected_devices, auto_connect_device


def load_test_settings() -> dict:
    """Load test settings from test-settings.json."""
    project_root = get_project_root()
    settings_path = project_root / "app" / "src" / "androidTest" / "assets" / "test-settings.json"
    with open(settings_path, "r", encoding="utf-8") as f:
        return json.load(f)


def main():
    """Main test script."""
    setup_windows_encoding()

    parser = argparse.ArgumentParser(description="Run Otter Android tests")
    parser.add_argument(
        "--unit",
        action="store_true",
        help="Run unit tests only"
    )
    parser.add_argument(
        "--instrumented",
        action="store_true",
        help="Run instrumented tests only (requires device)"
    )
    parser.add_argument(
        "--coverage",
        action="store_true",
        help="Generate coverage report (unit tests only)"
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
        log_path = setup_log_file(temp_dir, prefix="test")
        log(f"📝 Log file: {log_path}")
        log("=" * 80)

    log("🧪 Otter Test Runner")

    # Determine which tests to run
    run_unit = args.unit or args.coverage or (not args.instrumented)
    run_instrumented = args.instrumented or (not args.unit and not args.coverage)

    success = True

    # Unit tests
    if run_unit:
        print_header("Running Unit Tests")

        if args.coverage:
            log("🧪 Running unit tests with coverage...")
            if not run_gradle_task("testDebugUnitTestCoverage"):
                log("❌ Unit tests with coverage failed")
                success = False
            else:
                log("✅ Unit tests passed with coverage!")
                project_root = get_project_root()
                coverage_report = project_root / "app" / "build" / "reports" / "jacoco" / "jacocoMergedReport" / "html" / "index.html"
                if coverage_report.exists():
                    log(f"📊 Coverage report: {coverage_report}")
        else:
            log("🧪 Running unit tests...")
            if not run_gradle_task("testDebugUnitTest"):
                log("❌ Unit tests failed")
                success = False
            else:
                log("✅ Unit tests passed!")

    # Instrumented tests
    if run_instrumented:
        print_header("Running Instrumented Tests")

        # Check if device connected
        devices = get_connected_devices()

        if not devices:
            log("🔍 No device connected, attempting auto-connect...")
            connection = auto_connect_device()

            if not connection:
                log("❌ No device connected and auto-connect failed")
                log("💡 Instrumented tests require a connected device")
                log("   Connect via: python scripts/adb_auto_connect.py")
                return 1

            log(f"✅ Device connected: {connection}")
            devices = get_connected_devices()

        if not devices:
            log("❌ Device connected but not visible to adb")
            return 1

        device = devices[0]
        if len(devices) > 1:
            log(f"⚠️  Multiple devices connected, using: {device}")

        log(f"🧪 Running instrumented tests on {device}...")

        # Load timeout from test settings
        test_settings = load_test_settings()
        timeout = test_settings["test_execution"]["instrumented_timeout_seconds"]

        # Send test archives to device before running tests
        # (test package directory is cleaned on every build/install)
        log("📦 Preparing test archives on device...")
        project_root = get_project_root()
        test_archives_dir = project_root / test_settings["test_archives"]["host_path"]
        device_path = test_settings["test_archives"]["device_path"]

        # Create device directory
        subprocess.run(["adb", "-s", device, "shell", f"mkdir -p {device_path}"], check=False)

        # Send all test archives
        archives_sent = 0
        for archive_file in test_settings["test_archives"]["files"]:
            archive_path = test_archives_dir / archive_file
            if archive_path.exists():
                log(f"  📤 Sending {archive_file}...")
                result = subprocess.run(
                    ["adb", "-s", device, "push", str(archive_path), device_path],
                    capture_output=True
                )
                if result.returncode == 0:
                    archives_sent += 1
                else:
                    log(f"  ⚠️  Failed to send {archive_file}")
            else:
                log(f"  ⚠️  Archive not found: {archive_file}")

        log(f"✅ Sent {archives_sent}/{len(test_settings['test_archives']['files'])} archives")

        if not run_gradle_task("connectedDebugAndroidTest", timeout=timeout):
            log("❌ Instrumented tests failed")
            success = False
        else:
            log("✅ Instrumented tests passed!")

    # Summary
    log("\n" + "=" * 60)
    if success:
        log("✅ All tests passed!")
    else:
        log("❌ Some tests failed")
    log("=" * 60)

    # Append Gradle test reports to log
    if not args.no_log and log_path:
        project_root = get_project_root()
        build_results = project_root / "app" / "build" / "test-results"

        log("\n" + "=" * 80)
        log("📊 GRADLE TEST REPORTS")
        log("=" * 80)

        # Append all XML test results
        if build_results.exists():
            for xml_file in sorted(build_results.rglob("TEST-*.xml")):
                rel_path = xml_file.relative_to(project_root)
                log(f"\n{'─' * 80}")
                log(f"📄 {rel_path}")
                log(f"{'─' * 80}")
                try:
                    with open(xml_file, "r", encoding="utf-8") as f:
                        log(f.read())
                except Exception as e:
                    log(f"⚠️  Failed to read {xml_file}: {e}")
        else:
            log(f"\n⚠️  No Gradle test results found at: {build_results}")

        log("\n" + "=" * 80)
        log("📄 END OF REPORT")
        log("=" * 80)

    # Close log file
    if log_path:
        close_log_file()
        print(f"\n📝 Complete log with Gradle reports saved to: {log_path}")

    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
