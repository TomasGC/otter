#!/usr/bin/env python3
"""Android versioning utilities."""

import re
from pathlib import Path
from typing import Optional


class VersionManager:
    """Manages Android app versioning in build.gradle.kts.

    Pure static methods handle all string transformations (unit-testable with zero I/O).
    Instance methods perform file I/O (integration-testable with tmp_path).
    """

    def __init__(self, project_root: Path) -> None:
        self._project_root = project_root

    # -------------------------------------------------------------------------
    # Pure methods — no I/O, no mocks needed in unit tests
    # -------------------------------------------------------------------------

    @staticmethod
    def parse_version_code(content: str) -> int:
        match = re.search(r"versionCode\s*=\s*(\d+)", content)
        if not match:
            raise ValueError("versionCode not found in build.gradle.kts")
        return int(match.group(1))

    @staticmethod
    def parse_version_name(content: str) -> str:
        match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
        if not match:
            raise ValueError("versionName not found in build.gradle.kts")
        return match.group(1)

    @staticmethod
    def compute_new_name(old_name: str, new_code: int) -> str:
        parts = old_name.split(".")
        if len(parts) == 3:
            return f"{parts[0]}.{parts[1]}.{new_code}"
        return f"0.0.{new_code}"

    @staticmethod
    def apply_version(content: str, new_code: int, new_name: str) -> str:
        content = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {new_code}", content)
        content = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_name}"', content)
        return content

    # -------------------------------------------------------------------------
    # I/O methods — testable with real files via tmp_path
    # -------------------------------------------------------------------------

    def increment(self, gradle_path: Optional[Path] = None) -> tuple[int, str]:
        """Increment versionCode and versionName in build.gradle.kts."""
        path = gradle_path or (self._project_root / "app" / "build.gradle.kts")
        content = path.read_text(encoding="utf-8")
        old_code = self.parse_version_code(content)
        old_name = self.parse_version_name(content)
        new_code = old_code + 1
        new_name = self.compute_new_name(old_name, new_code)
        path.write_text(self.apply_version(content, new_code, new_name), encoding="utf-8")
        return new_code, new_name

    def get_apk_path(self, variant: str = "debug") -> Path | None:
        """Return path to the built APK, or None if not found."""
        apk_dir = self._project_root / "app" / "build" / "outputs" / "apk" / variant
        if not apk_dir.exists():
            return None
        apks = list(apk_dir.glob("*.apk"))
        return apks[0] if apks else None
