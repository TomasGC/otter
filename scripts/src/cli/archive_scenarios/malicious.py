"""Malicious archive fixtures — ZIP path-traversal payload.

ZIP-only: RAR/7z can't have arbitrary entry names injected via the available CLI tools,
and TAR/GZIP/RPA have no equivalent extraction path in this app worth exercising this
way. Mirrors the payload shape of the Kotlin TestArchiveHelper.createMaliciousZipWith-
PathTraversal() it replaces.
"""

import zipfile
from pathlib import Path
from typing import Optional

from cli.archive_scenarios.base import ArchiveScenario

TRAVERSAL_ENTRY_NAME = "../../../etc/malicious.txt"
NORMAL_ENTRY_NAME = "normal.txt"


class MaliciousArchives(ArchiveScenario):
    FILE_PREFIX = "malicious_test_archive"

    def __init__(self, output_dir: Path) -> None:
        self._output_dir = output_dir

    def create_all(self) -> dict[str, Optional[Path]]:
        return {"zip": self._create_malicious_zip()}

    def _create_malicious_zip(self) -> Path:
        out = self._output_dir / f"{self.FILE_PREFIX}.zip"
        if out.exists():
            print(f"  [SKIP] {out.name} already exists")
            return out

        with zipfile.ZipFile(out, "w") as zf:
            zf.writestr(TRAVERSAL_ENTRY_NAME, "Malicious content")
            zf.writestr(NORMAL_ENTRY_NAME, "Normal content")

        print(f"  Malicious archive created: {out.name}")
        return out
