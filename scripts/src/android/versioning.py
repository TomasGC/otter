#!/usr/bin/env python3
"""Android versioning utilities."""

import re
from pathlib import Path

# Import from common
import sys
sys.path.insert(0, str(Path(__file__).parent.parent))
from common.file_utils import get_project_root


def increment_version(build_gradle_path: Path) -> tuple[int, str]:
    """
    Increment versionCode and versionName in build.gradle.kts.

    Returns:
        Tuple of (new_version_code, new_version_name)
    """
    content = build_gradle_path.read_text(encoding="utf-8")

    # Extract current versionCode
    version_code_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    if not version_code_match:
        raise ValueError("versionCode not found in build.gradle.kts")

    current_code = int(version_code_match.group(1))
    new_code = current_code + 1

    # Extract current versionName
    version_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    if not version_name_match:
        raise ValueError("versionName not found in build.gradle.kts")

    current_name = version_name_match.group(1)
    # Assume format: major.minor.patch
    parts = current_name.split(".")
    if len(parts) == 3:
        new_name = f"{parts[0]}.{parts[1]}.{new_code}"
    else:
        new_name = f"0.0.{new_code}"

    # Replace in content
    content = re.sub(
        r'versionCode\s*=\s*\d+',
        f'versionCode = {new_code}',
        content
    )
    content = re.sub(
        r'versionName\s*=\s*"[^"]+"',
        f'versionName = "{new_name}"',
        content
    )

    build_gradle_path.write_text(content, encoding="utf-8")

    return new_code, new_name


def get_apk_path(variant: str = "debug") -> Path | None:
    """
    Get path to built APK.

    Args:
        variant: Build variant (debug/release)

    Returns:
        Path to APK or None if not found
    """
    project_root = get_project_root()
    apk_dir = project_root / "app" / "build" / "outputs" / "apk" / variant

    if not apk_dir.exists():
        return None

    apk_files = list(apk_dir.glob("*.apk"))
    if not apk_files:
        return None

    return apk_files[0]
