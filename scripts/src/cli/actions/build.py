"""Build action — build and optionally install Android APK."""

from pathlib import Path
from typing import Optional

from android import AdbManager, GradleRunner, VersionManager
from common.file_utils import get_project_root
from common.subprocess_runner import SubprocessRunner


class BuildAction:
    def __init__(
        self,
        runner: SubprocessRunner,
        gradle: Optional[GradleRunner] = None,
        adb: Optional[AdbManager] = None,
        version_mgr: Optional[VersionManager] = None,
        project_root: Optional[Path] = None,
        connector=None,  # Optional[DeviceConnector] — lazily created if None
    ) -> None:
        self._runner = runner
        self._project_root = project_root or get_project_root()
        self._gradle = gradle or GradleRunner(runner, self._project_root)
        self._adb = adb or AdbManager(runner)
        self._version_mgr = version_mgr or VersionManager(self._project_root)
        self._connector = connector

    def _get_connector(self):
        if self._connector is None:
            from cli.adb_connect import DeviceConnector

            config = self._project_root / "temp" / ".adb_device_cache.json"
            self._connector = DeviceConnector(self._runner, config)
        return self._connector

    def increment_version(self) -> tuple[int, str]:
        return self._version_mgr.increment()

    def build_apk(self, variant: str = "debug") -> bool:
        return self._gradle.run_task(f"assemble{variant.capitalize()}")

    def get_apk_path(self, variant: str = "debug") -> Optional[Path]:
        return self._version_mgr.get_apk_path(variant)

    def run(self, install: bool = True) -> int:
        try:
            new_code, new_name = self.increment_version()
            print(f"Version: {new_name} ({new_code})")
        except Exception as e:
            print(f"Failed to increment version: {e}")
            return 1

        if not self.build_apk():
            print("Build failed")
            return 1

        apk_path = self.get_apk_path()
        if not apk_path:
            print("APK not found after build")
            return 1

        print(f"APK: {apk_path.relative_to(self._project_root)}")

        if not install:
            print("Build complete (install skipped)")
            return 0

        devices = self._adb.get_connected()
        if not devices:
            device = self._get_connector().auto_connect()
            if not device:
                print("No device connected — build succeeded, install skipped")
                print(f"Install manually: adb install -r {apk_path}")
                return 0
        else:
            device = devices[0]

        if not self._adb.install_apk(apk_path, device):
            print("Installation failed")
            return 1

        print("App installed successfully")
        return 0
