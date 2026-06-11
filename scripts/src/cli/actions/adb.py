"""ADB action — connect to Android device and send files."""

from pathlib import Path
from typing import Optional

from common.file_utils import get_project_root, load_test_settings
from common.subprocess_runner import SubprocessRunner

SUBVERBS = ["connect", "send"]


class AdbAction:
    def __init__(self, runner: SubprocessRunner, test_settings: Optional[dict] = None) -> None:
        self._runner = runner
        self._project_root = get_project_root()
        self._settings = test_settings

    def _get_settings(self) -> dict:
        if self._settings is None:
            self._settings = load_test_settings()
        return self._settings

    def _get_connector(self):
        from cli.adb_connect import DeviceConnector
        config = self._project_root / "temp" / ".adb_device_cache.json"
        return DeviceConnector(self._runner, config)

    def _get_pusher(self):
        from cli.send_to_phone import FilePusher
        return FilePusher(self._runner)

    def run_connect(
        self,
        device: Optional[str] = None,
        pair: Optional[str] = None,
        pair_address: Optional[str] = None,
    ) -> int:
        result = self._get_connector().auto_connect(
            target_device=device,
            pairing_code=pair,
            pairing_address=pair_address,
        )
        return 0 if result else 1

    def run_send(
        self,
        files: Optional[list] = None,
        dest: Optional[str] = None,
        ci: bool = False,
    ) -> int:
        if dest is None or files is None:
            settings = self._get_settings()
            if dest is None:
                dest = settings["test_archives"]["device_path"]
            if files is None:
                files = [
                    self._project_root / settings["test_archives"]["host_path"] / fn
                    for fn in settings["test_archives"]["files"]
                ]
        pusher = self._get_pusher()
        if not pusher.ensure_connection(non_interactive=ci):
            return 1
        _, fail = pusher.push_files(list(files), dest)
        return 0 if fail == 0 else 1
