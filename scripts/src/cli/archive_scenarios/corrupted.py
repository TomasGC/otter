"""Corrupted archive fixtures — format-agnostic byte mutation of a valid archive.

Works uniformly across every format because it mutates raw bytes of an already-valid
archive rather than encoding a new one. This is what fixes the RAR/7z corrupted-archive
test: the old Kotlin TestArchiveHelper.createCorruptedZip() wrote ZIP-shaped bytes under
a .rar/.7z extension, and 7-Zip-JBinding's format auto-detection opened it as a valid ZIP
anyway, so the extractor never saw anything corrupted for that format.
"""

from pathlib import Path
from typing import Optional

from cli.archive_scenarios.base import ArchiveScenario

TRUNCATE_RATIO = 0.6
MUTATE_BYTE_COUNT = 8
MID_STREAM_MUTATE_FRACTIONS = (0.1, 0.3, 0.5, 0.7, 0.9)
MID_STREAM_BLOCK_SIZE = 64


class CorruptedArchives(ArchiveScenario):
    def __init__(self, sources: dict[str, Optional[Path]], output_dir: Path) -> None:
        self._sources = sources
        self._output_dir = output_dir

    def create_all(self) -> dict[str, Optional[Path]]:
        return {name: self._corrupt(source) for name, source in self._sources.items()}

    def _corrupt(self, source: Optional[Path]) -> Optional[Path]:
        if source is None or not source.exists():
            return None

        out = self._output_dir / f"corrupted_{source.name}"
        if out.exists():
            print(f"  [SKIP] {out.name} already exists")
            return out

        data = bytearray(source.read_bytes())
        data = data[: max(1, int(len(data) * TRUNCATE_RATIO))]

        # Flip a contiguous block at each fraction of the stream, not just the tail — some
        # formats (e.g. RAR) store headers/CRCs throughout the stream, not only at the end,
        # and LZ-based decompressors can be tolerant of a single flipped byte landing between
        # compression blocks, so a block-sized mutation is needed to reliably break decoding.
        for fraction in MID_STREAM_MUTATE_FRACTIONS:
            offset = int(len(data) * fraction)
            end = min(offset + MID_STREAM_BLOCK_SIZE, len(data))
            for i in range(offset, end):
                data[i] ^= 0xFF

        for i in range(min(MUTATE_BYTE_COUNT, len(data))):
            data[-(i + 1)] ^= 0xFF

        out.write_bytes(bytes(data))
        print(f"  Corrupted archive created: {out.name}")
        return out
