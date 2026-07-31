#!/usr/bin/env python3
"""Send files to Android phone via ADB with automatic connection."""

import subprocess
import sys
from pathlib import Path
from typing import Callable

from common.constants import TIMEOUT_FILE_CHECK
from common.file_utils import get_project_root, load_test_settings
from common.subprocess_runner import RealSubprocessRunner, SubprocessRunner


class FilePusher:
    """Pushes local files to an Android device via ADB.

    Inject `SubprocessRunner` for testability; inject `connector` (a DeviceConnector
    instance) to override the auto-connect logic in unit tests.
    """

    def __init__(
        self,
        runner: SubprocessRunner,
        connector=None,  # Optional[DeviceConnector] — lazily created if None
        input_fn: Callable[[str], str] = input,
    ) -> None:
        self._runner = runner
        self._connector = connector
        self._input_fn = input_fn

    # ------------------------------------------------------------------
    # Connection
    # ------------------------------------------------------------------

    def _get_connector(self):
        if self._connector is None:
            from cli.adb_connect import DeviceConnector

            config = get_project_root() / "temp" / ".adb_device_cache.json"
            self._connector = DeviceConnector(self._runner, config, input_fn=self._input_fn)
        return self._connector

    def ensure_connection(self, non_interactive: bool = False) -> bool:
        """Return True if a device is ready for ADB commands."""
        print("Checking ADB connection...")
        sys.stdout.flush()

        connector = self._get_connector()
        result = connector.auto_connect()
        if result is not None:
            print("Device connected")
            return True
        if non_interactive:
            print("Device not connected. Run manually: python manage.py adb connect")
        return False

    # ------------------------------------------------------------------
    # Push
    # ------------------------------------------------------------------

    def push_file(self, local_path: Path, remote_dir: str) -> bool:
        """Push one file to `remote_dir` on the device. Skips if already present."""
        if not local_path.exists():
            print(f"File not found: {local_path}")
            return False

        remote_file = f"{remote_dir}/{local_path.name}"

        print(f"\nChecking: {local_path.name}")
        check = self._runner.run(
            ["adb", "shell", f"test -f {remote_file} && echo exists || echo missing"],
            capture_output=True,
            text=True,
            timeout=TIMEOUT_FILE_CHECK,
        )
        if "exists" in check.stdout:
            print("Already exists on device, skipping")
            return True

        size_mb = local_path.stat().st_size / (1024 * 1024)
        print(f"Pushing: {local_path.name} ({size_mb:.2f} MB)")
        print(f"   Destination: {remote_dir}")

        mkdir = self._runner.run(
            ["adb", "shell", f"mkdir -p {remote_dir}"],
            capture_output=True,
            text=True,
            timeout=TIMEOUT_FILE_CHECK,
        )
        if mkdir.returncode != 0:
            print(f"Failed to create directory: {remote_dir}")
            print(mkdir.stderr)
            return False

        proc = self._runner.popen(
            ["adb", "push", str(local_path), remote_dir],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        for line in proc.stdout:
            stripped = line.strip()
            if stripped:
                print(f"   {stripped}", end="\r", flush=True)
        proc.stdout.close()
        proc.wait()

        print()
        if proc.returncode == 0:
            print("Transferred")
            return True
        print("Transfer failed")
        return False

    def push_files(self, files: list[Path], remote_dir: str) -> tuple[int, int]:
        """Push multiple files. Returns (success_count, fail_count)."""
        success, fail = 0, 0
        for f in files:
            if self.push_file(f, remote_dir):
                success += 1
            else:
                fail += 1
        return success, fail


def resolve_archive_files(project_root: Path, test_settings: dict) -> list[Path]:
    """Return all archive files to push, expanding glob patterns from test_settings."""
    ta = test_settings.get("test_archives", {})
    host_dir = project_root / ta.get("host_path", "archives")
    files: list[Path] = [host_dir / f for f in ta.get("files", [])]
    for pattern in ta.get("glob_files", []):
        files.extend(sorted(host_dir.glob(pattern)))
    return files


def main() -> None:  # pragma: no cover
    import argparse
    import io

    if sys.platform == "win32":
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8")

    test_settings = load_test_settings()
    default_dest = test_settings["test_archives"]["device_path"]

    parser = argparse.ArgumentParser(description="Send files to Android phone via ADB")
    parser.add_argument("files", nargs="*", help="File(s) to send (empty = all test archives from test-settings.json)")
    parser.add_argument("--dest", default=default_dest, help=f"Destination path on device (default: {default_dest})")
    parser.add_argument(
        "--ci", action="store_true", help="Non-interactive mode (fails if device not already connected)"
    )
    args = parser.parse_args()

    project_root = get_project_root()
    if not args.files:
        resolved = resolve_archive_files(project_root, test_settings)
        args.files = [str(f) for f in resolved]
        print(f"Sending {len(args.files)} test archives from configuration...")

    pusher = FilePusher(RealSubprocessRunner())

    if not pusher.ensure_connection(non_interactive=args.ci):
        print("\nMake sure 'Wireless debugging' is enabled on your device")
        sys.exit(1)

    success, fail = pusher.push_files([Path(f) for f in args.files], args.dest)

    print(f"\n{'='*60}")
    print(f"Transferred: {success}")
    if fail > 0:
        print(f"Failed: {fail}")
    print(f"{'='*60}")

    sys.exit(0 if fail == 0 else 1)


if __name__ == "__main__":  # pragma: no cover
    main()
