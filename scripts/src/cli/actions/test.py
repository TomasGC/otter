"""Test action — run Android app tests (unit, instrumented, coverage)."""

from pathlib import Path
from typing import Optional

from android import AdbManager, GradleRunner
from common.file_utils import get_project_root, load_test_settings
from common.subprocess_runner import SubprocessRunner

SUITES = ["unit", "instrumented", "coverage"]


class TestAction:
    def __init__(
        self,
        runner: SubprocessRunner,
        gradle: Optional[GradleRunner] = None,
        adb: Optional[AdbManager] = None,
        project_root: Optional[Path] = None,
        test_settings: Optional[dict] = None,
        connector=None,  # Optional[DeviceConnector] — lazily created if None
    ) -> None:
        self._runner = runner
        self._project_root = project_root or get_project_root()
        self._gradle = gradle or GradleRunner(runner, self._project_root)
        self._adb = adb or AdbManager(runner)
        self._settings = test_settings
        self._connector = connector

    def _get_connector(self):
        if self._connector is None:
            from cli.adb_connect import DeviceConnector

            config = self._project_root / "temp" / ".adb_device_cache.json"
            self._connector = DeviceConnector(self._runner, config)
        return self._connector

    def _get_settings(self) -> dict:
        if self._settings is None:
            self._settings = load_test_settings()
        return self._settings

    def run_unit(self, coverage: bool = False) -> bool:
        task = "testDebugUnitTestCoverage" if coverage else "testDebugUnitTest"
        return self._gradle.run_task(task)

    def send_archives(self, device: str) -> int:
        settings = self._get_settings()
        device_path = settings["test_archives"]["device_path"]
        host_path = self._project_root / settings["test_archives"]["host_path"]
        self._runner.run(
            ["adb", "-s", device, "shell", f"mkdir -p {device_path}"],
            capture_output=True,
        )
        sent = 0
        for filename in settings["test_archives"]["files"]:
            src = host_path / filename
            if src.exists():
                result = self._runner.run(
                    ["adb", "-s", device, "push", str(src), device_path],
                    capture_output=True,
                )
                if result.returncode == 0:
                    sent += 1
        return sent

    def run_instrumented(self, device: str) -> bool:
        settings = self._get_settings()
        timeout = settings["test_execution"]["instrumented_timeout_seconds"]
        self.send_archives(device)
        return self._gradle.run_task("connectedDebugAndroidTest", timeout=timeout)

    def run(self, suites: list[str] | None = None) -> int:
        suites = suites or []

        if "coverage" in suites:
            return 0 if self.run_unit(coverage=True) else 1

        run_unit = not suites or "unit" in suites
        run_instrumented = not suites or "instrumented" in suites
        success = True

        if run_unit:
            if not self.run_unit():
                success = False

        if run_instrumented:
            devices = self._adb.get_connected()
            if not devices:
                device = self._get_connector().auto_connect()
                if not device:
                    print("No device connected for instrumented tests")
                    return 1
                devices = self._adb.get_connected()
            if not devices:
                print("Device connected but not visible to adb")
                return 1
            if not self.run_instrumented(devices[0]):
                success = False

        return 0 if success else 1
