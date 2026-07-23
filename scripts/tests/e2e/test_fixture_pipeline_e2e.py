#!/usr/bin/env python3
"""E2E test: create fixture archives for real, push to a real connected device,
verify they actually land there. Requires a connected ADB device/emulator --
auto-skipped otherwise (see conftest.py's pytest_collection_modifyitems).
"""

import subprocess

import pytest

from cli.archive_scenarios.orchestrator import create_all_fixture_archives
from cli.send_to_phone import FilePusher
from common.subprocess_runner import RealSubprocessRunner

pytestmark = pytest.mark.e2e

REMOTE_DIR = "/data/local/tmp/otter-e2e-fixture-test"

# Every format the orchestrator's PerfectArchives/CorruptedArchives/LargeArchives/
# DeepNestedArchives/LongFilenameArchives scenarios produce from a non-empty template.
EXPECTED_FORMAT_EXTENSIONS = (".rpa", ".zip", ".tar.gz", ".tar.bz2", ".tar", ".gz", ".7z", ".rar")


def _extension_of(filename: str) -> str:
    return next((ext for ext in EXPECTED_FORMAT_EXTENSIONS if filename.endswith(ext)), "")


def _connected_device() -> str:
    result = subprocess.run(["adb", "devices"], capture_output=True, text=True, timeout=5)
    for line in result.stdout.splitlines()[1:]:
        if line.strip() and "\tdevice" in line:
            return line.split("\t")[0]
    raise RuntimeError("No connected device -- should have been skipped by conftest.py")


class TestFixtureCreationAndPushE2E:
    def test_created_fixtures_land_on_device(self, docker_tmp):
        device = _connected_device()

        out = docker_tmp / "out"
        out.mkdir()
        template = docker_tmp / "template"
        template.mkdir()
        (template / "file.txt").write_text("e2e content", encoding="utf-8")

        create_all_fixture_archives(RealSubprocessRunner(), out, template)

        created_files = [f for f in out.iterdir() if f.is_file()]
        assert created_files, "orchestrator produced no files to push"

        # Guard against the orchestrator silently producing 0 files for one format
        # (e.g. a RarDockerFormat/SevenZipFormat regression) rather than failing loudly here.
        produced_extensions = {_extension_of(f.name) for f in created_files}
        missing_formats = set(EXPECTED_FORMAT_EXTENSIONS) - produced_extensions
        assert not missing_formats, f"No fixture produced for format(s): {missing_formats}"

        pusher = FilePusher(RealSubprocessRunner())
        success, fail = pusher.push_files(created_files, REMOTE_DIR)

        assert fail == 0
        assert success == len(created_files)

        listing = subprocess.run(
            ["adb", "-s", device, "shell", f"ls {REMOTE_DIR}"],
            capture_output=True,
            text=True,
            timeout=15,
        )
        remote_names = set(listing.stdout.split())
        missing_on_device = [f.name for f in created_files if f.name not in remote_names]
        assert not missing_on_device, f"Fixtures created locally but missing on device: {missing_on_device}"

    @classmethod
    def teardown_class(cls):
        subprocess.run(["adb", "shell", f"rm -rf {REMOTE_DIR}"], capture_output=True, timeout=15)
