"""Test action — run Android app tests (unit, instrumented, coverage)."""

from pathlib import Path
from typing import Optional

from android import AdbManager, GradleRunner
from common.file_utils import get_project_root, load_test_settings
from common.subprocess_runner import SubprocessRunner

_APP_PACKAGE = "app.otter"

SUITES = [
    "unit",
    "integration-mocks",
    "integration-reals",
    "integrations",
    "instrumented",
]

# This test revokes its own app's POST_NOTIFICATIONS permission while its process is
# alive. Revoking a permission from a live process makes the OS kill that process
# outright (ActivityManager: "Killing ... permissions revoked") -- which kills the
# instrumented test code running inside it too, taking down every test queued after
# it in the same run. It must run in its own isolated instrumentation invocation,
# bracketed by adb pm revoke/grant from the host, with the permission already denied
# before its process starts.
PERMISSION_ISOLATED_TEST = (
    "app.otter.ExtractionActivityContentUriTest#" "postNotificationsDenied_extractionStillStarts_noPermissionCrash"
)
PERMISSION_ISOLATED_PACKAGE = "app.otter"
PERMISSION_ISOLATED_PERMISSION = "android.permission.POST_NOTIFICATIONS"


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

    def run_unit(self) -> bool:
        ok = True
        for test_type in ["unit-domain-service", "unit-data", "unit-ui"]:
            if not self._gradle.run_task("testDebugUnitTest", extra_args=[f"-DtestType={test_type}"]):
                ok = False
        return ok

    def run_integration_mocks(self) -> bool:
        ok = True
        for test_type in ["integration-mock-extractor", "integration-mock-other"]:
            if not self._gradle.run_task("testDebugUnitTest", extra_args=[f"-DtestType={test_type}"]):
                ok = False
        return ok

    def run_integration_reals(self) -> bool:
        return self._gradle.run_task("testDebugUnitTest", extra_args=["-DtestType=integration-real"])

    def send_archives(self, device: str) -> int:
        from cli.send_to_phone import resolve_archive_files

        settings = self._get_settings()
        device_path = settings["test_archives"]["device_path"]
        archive_files = resolve_archive_files(self._project_root, settings)
        self._runner.run(
            ["adb", "-s", device, "shell", f"mkdir -p {device_path}"],
            capture_output=True,
        )
        sent = 0
        for src in archive_files:
            if src.exists():
                result = self._runner.run(
                    ["adb", "-s", device, "push", str(src), device_path],
                    capture_output=True,
                )
                if result.returncode == 0:
                    sent += 1
        return sent

    def _grant_manage_external_storage(self, device: str) -> bool:
        # Build both APKs then install directly on the target ADB device.
        # Gradle's installDebug can target a managed-device AVD instead of the
        # ADB-connected device, causing appops set to fail with "No UID".
        from cli.actions.build import BuildAction

        build = BuildAction(
            self._runner,
            gradle=self._gradle,
            adb=self._adb,
            project_root=self._project_root,
        )
        if not build.build_apk() or not build.build_test_apk():
            return False
        main_apk = build.get_apk_path()
        test_apk = build.get_test_apk_path()
        if not main_apk or not self._adb.install_apk(main_apk, device):
            return False
        if not test_apk or not self._adb.install_apk(test_apk, device):
            return False
        result = self._runner.run(
            [
                "adb",
                "-s",
                device,
                "shell",
                "appops",
                "set",
                _APP_PACKAGE,
                "MANAGE_EXTERNAL_STORAGE",
                "allow",
            ]
        )
        return result.returncode == 0

    def run_instrumented(self, device: str) -> bool:
        import os

        settings = self._get_settings()
        timeout = settings["test_execution"]["instrumented_timeout_seconds"]
        if not self._grant_manage_external_storage(device):
            print("ERROR: failed to grant MANAGE_EXTERNAL_STORAGE")
            return False
        self.send_archives(device)
        os.environ["OTTER_ARCHIVES_PUSHED"] = "1"
        try:
            main_ok = self._gradle.run_task(
                "connectedDebugAndroidTest",
                timeout=timeout,
                extra_args=["-Pandroid.testInstrumentationRunnerArguments.notClass=" f"{PERMISSION_ISOLATED_TEST}"],
            )
            isolated_ok = self.run_permission_isolated_test(device, timeout)
        finally:
            os.environ.pop("OTTER_ARCHIVES_PUSHED", None)
        return main_ok and isolated_ok

    def run_permission_isolated_test(self, device: str, timeout: int) -> bool:
        self._runner.run(
            [
                "adb",
                "-s",
                device,
                "shell",
                "pm",
                "revoke",
                PERMISSION_ISOLATED_PACKAGE,
                PERMISSION_ISOLATED_PERMISSION,
            ],
            capture_output=True,
        )
        ok = self._gradle.run_task(
            "connectedDebugAndroidTest",
            timeout=timeout,
            extra_args=[f"-Pandroid.testInstrumentationRunnerArguments.class={PERMISSION_ISOLATED_TEST}"],
        )
        self._runner.run(
            [
                "adb",
                "-s",
                device,
                "shell",
                "pm",
                "grant",
                PERMISSION_ISOLATED_PACKAGE,
                PERMISSION_ISOLATED_PERMISSION,
            ],
            capture_output=True,
        )
        return ok

    def run(self, suites: list[str] | None = None) -> int:
        suites = suites or []
        run_all = not suites
        run_unit_flag = run_all or "unit" in suites
        run_integ_mocks = run_all or "integration-mocks" in suites or "integrations" in suites
        run_integ_reals = run_all or "integration-reals" in suites or "integrations" in suites
        run_instrumented = run_all or "instrumented" in suites
        success = True

        if run_unit_flag:
            if not self.run_unit():
                success = False

        if run_integ_mocks:
            if not self.run_integration_mocks():
                success = False

        if run_integ_reals:
            if not self.run_integration_reals():
                success = False

        if run_instrumented:
            devices = self._adb.get_connected()
            if not devices:
                device = self._get_connector().auto_connect()
                if device:
                    devices = self._adb.get_connected()
            if not devices:
                device = self._ensure_emulator()
                if not device:
                    print("No device connected for instrumented tests")
                    return 1
                devices = [device]
            if not self.run_instrumented(devices[0]):
                success = False

        return 0 if success else 1

    def _ensure_emulator(self) -> str | None:
        emulators = self._adb.get_running_emulators()
        if emulators:
            print(f"Found running emulator: {emulators[0]}, waiting for ready state...")
            return self._adb.wait_for_emulator()
        avds = self._adb.list_avds()
        if not avds:
            print("No AVD found — create one in Android Studio")
            return None
        print(f"Starting emulator: {avds[0]}")
        if not self._adb.start_emulator(avds[0]):
            return None
        return self._adb.wait_for_emulator()
