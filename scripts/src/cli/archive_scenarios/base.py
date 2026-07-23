"""Shared abstractions for archive test-fixture scenarios (Perfect, Corrupted, Empty, ...)."""

from abc import ABC, abstractmethod
from pathlib import Path
from typing import Optional


class ArchiveScenario(ABC):
    """Top-level interface for one fixture scenario (e.g. Perfect, Corrupted, Malicious).

    Each scenario produces a set of archives, one per format it supports, keyed by
    format name. Callers iterate a list[ArchiveScenario] polymorphically without
    knowing which concrete scenario they're driving.
    """

    @abstractmethod
    def create_all(self) -> dict[str, Optional[Path]]: ...


class ArchiveFormat(ABC):
    """Per-format leaf used by template-dir-based scenarios (Perfect, Empty, Stress).

    file_prefix lets the same format classes be reused across scenarios that only
    differ by which template directory they read from and what the output is named
    (e.g. "test_archive" vs "empty_test_archive").
    """

    def __init__(self, output_dir: Path, template_dir: Path, file_prefix: str = "test_archive") -> None:
        self._output_dir = output_dir
        self._template_dir = template_dir
        self._file_prefix = file_prefix

    @property
    @abstractmethod
    def name(self) -> str: ...

    @abstractmethod
    def create(self) -> Optional[Path]: ...

    def _output_path(self, ext: str) -> Path:
        return self._output_dir / f"{self._file_prefix}.{ext}"

    def _skip_if_exists(self, path: Path) -> Optional[Path]:
        if path.exists():
            print(f"  [SKIP] {path.name} already exists")
            return path
        return None


class ArchiveFormatScenario(ArchiveScenario):
    """Generic ArchiveScenario that fans out create() across a list of ArchiveFormat."""

    def __init__(self, formats: list[ArchiveFormat]) -> None:
        self._formats = formats

    def create_all(self) -> dict[str, Optional[Path]]:
        return {fmt.name: fmt.create() for fmt in self._formats}
