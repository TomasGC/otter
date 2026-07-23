#!/usr/bin/env python3
"""Generate test archive structure with real, valid files."""

import random as _random
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.parent.parent.parent
OUTPUT_DIR = PROJECT_ROOT / "archives" / "template"

FILE_TYPES = [
    (".txt", 25),
    (".csv", 15),
    (".pdf", 10),
    (".jpg", 15),
    (".png", 10),
    (".gif", 5),
    (".webp", 5),
    (".webm", 10),
    (".mp4", 5),
]

_PDF_BYTES = b"""%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj
xref
0 4
trailer<</Size 4/Root 1 0 R>>
startxref
130
%%EOF
"""
_JPG_BYTES = bytes(
    [
        0xFF,
        0xD8,
        0xFF,
        0xE0,
        0x00,
        0x10,
        0x4A,
        0x46,
        0x49,
        0x46,
        0x00,
        0x01,
        0x01,
        0x01,
        0x00,
        0x48,
        0x00,
        0x48,
        0x00,
        0x00,
        0xFF,
        0xDB,
        0x00,
        0x43,
        0x00,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xC0,
        0x00,
        0x0B,
        0x08,
        0x00,
        0x01,
        0x00,
        0x01,
        0x01,
        0x01,
        0x11,
        0x00,
        0xFF,
        0xC4,
        0x00,
        0x14,
        0x00,
        0x01,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x03,
        0xFF,
        0xC4,
        0x00,
        0x14,
        0x10,
        0x01,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0xFF,
        0xDA,
        0x00,
        0x08,
        0x01,
        0x01,
        0x00,
        0x00,
        0x3F,
        0x00,
        0x37,
        0xFF,
        0xD9,
    ]
)
_PNG_BYTES = bytes(
    [
        0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
        0x00,
        0x0D,
        0x49,
        0x48,
        0x44,
        0x52,
        0x00,
        0x00,
        0x00,
        0x01,
        0x00,
        0x00,
        0x00,
        0x01,
        0x08,
        0x02,
        0x00,
        0x00,
        0x00,
        0x90,
        0x77,
        0x53,
        0xDE,
        0x00,
        0x00,
        0x00,
        0x0C,
        0x49,
        0x44,
        0x41,
        0x54,
        0x08,
        0xD7,
        0x63,
        0xF8,
        0xCF,
        0xC0,
        0x00,
        0x00,
        0x03,
        0x01,
        0x01,
        0x00,
        0x18,
        0xDD,
        0x8D,
        0xB4,
        0x00,
        0x00,
        0x00,
        0x00,
        0x49,
        0x45,
        0x4E,
        0x44,
        0xAE,
        0x42,
        0x60,
        0x82,
    ]
)
_GIF_BYTES = bytes(
    [
        0x47,
        0x49,
        0x46,
        0x38,
        0x39,
        0x61,
        0x01,
        0x00,
        0x01,
        0x00,
        0x80,
        0x00,
        0x00,
        0xFF,
        0xFF,
        0xFF,
        0x00,
        0x00,
        0x00,
        0x21,
        0xF9,
        0x04,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x2C,
        0x00,
        0x00,
        0x00,
        0x00,
        0x01,
        0x00,
        0x01,
        0x00,
        0x00,
        0x02,
        0x02,
        0x44,
        0x01,
        0x00,
        0x3B,
    ]
)
_WEBP_BYTES = bytes(
    [
        0x52,
        0x49,
        0x46,
        0x46,
        0x1A,
        0x00,
        0x00,
        0x00,
        0x57,
        0x45,
        0x42,
        0x50,
        0x56,
        0x50,
        0x38,
        0x4C,
        0x0D,
        0x00,
        0x00,
        0x00,
        0x2F,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x41,
        0x9C,
        0x01,
        0x4A,
        0x00,
        0x9D,
    ]
)
_MP4_BYTES = bytes(
    [
        0x00,
        0x00,
        0x00,
        0x18,
        0x66,
        0x74,
        0x79,
        0x70,
        0x6D,
        0x70,
        0x34,
        0x32,
        0x00,
        0x00,
        0x00,
        0x00,
        0x6D,
        0x70,
        0x34,
        0x32,
        0x69,
        0x73,
        0x6F,
        0x6D,
        0x00,
        0x00,
        0x00,
        0x08,
        0x6D,
        0x64,
        0x61,
        0x74,
    ]
)
_WEBM_BYTES = bytes(
    [
        0x1A,
        0x45,
        0xDF,
        0xA3,
        0x9F,
        0x42,
        0x86,
        0x81,
        0x01,
        0x42,
        0xF7,
        0x81,
        0x01,
        0x42,
        0xF2,
        0x81,
        0x04,
        0x42,
        0xF3,
        0x81,
        0x08,
        0x42,
        0x82,
        0x84,
        0x77,
        0x65,
        0x62,
        0x6D,
        0x42,
        0x87,
        0x81,
        0x02,
        0x42,
        0x85,
        0x81,
        0x02,
        0x18,
        0x53,
        0x80,
        0x67,
        0x01,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x1F,
    ]
)

_BINARY_CONTENT: dict[str, bytes] = {
    ".pdf": _PDF_BYTES,
    ".jpg": _JPG_BYTES,
    ".jpeg": _JPG_BYTES,
    ".png": _PNG_BYTES,
    ".gif": _GIF_BYTES,
    ".webp": _WEBP_BYTES,
    ".mp4": _MP4_BYTES,
    ".webm": _WEBM_BYTES,
}


class ArchiveTemplateGenerator:
    """Generates deterministic or random test file trees.

    Inject a seeded `random.Random` to get reproducible output in tests.
    """

    FILE_TYPES = FILE_TYPES
    DEFAULT_STRUCTURE = [
        ("folder_30", 30),
        ("folder_80", 80),
        ("folder_300", 300),
        ("folder_500", 500),
        ("folder_1000", 1000),
        ("folder_2000", 2000),
    ]
    ROOT_COUNT = 100

    def __init__(self, rng: _random.Random | None = None) -> None:
        self._rng = rng or _random.Random()

    # ------------------------------------------------------------------
    # Pure static helpers (zero I/O — fully unit-testable)
    # ------------------------------------------------------------------

    @staticmethod
    def pdf_bytes() -> bytes:
        return _PDF_BYTES

    @staticmethod
    def jpg_bytes() -> bytes:
        return _JPG_BYTES

    @staticmethod
    def png_bytes() -> bytes:
        return _PNG_BYTES

    @staticmethod
    def gif_bytes() -> bytes:
        return _GIF_BYTES

    @staticmethod
    def webp_bytes() -> bytes:
        return _WEBP_BYTES

    @staticmethod
    def mp4_bytes() -> bytes:
        return _MP4_BYTES

    @staticmethod
    def webm_bytes() -> bytes:
        return _WEBM_BYTES

    @staticmethod
    def make_txt_content(rng: _random.Random) -> str:
        lines = rng.randint(1, 5)
        return "\n".join([f"Line {i} with random data: {rng.randint(1000, 9999)}" for i in range(lines)])

    @staticmethod
    def make_csv_content(rng: _random.Random) -> str:
        rows = rng.randint(1, 5)
        content = "Name,Value,Date\n"
        for i in range(rows):
            content += f"Item{i},{rng.randint(1, 999)},2024-01-{rng.randint(1, 28):02d}\n"
        return content

    @staticmethod
    def random_extension(rng: _random.Random, file_types: list[tuple[str, int]] = FILE_TYPES) -> str:
        total = sum(w for _, w in file_types)
        r = rng.randint(0, total - 1)
        cumulative = 0
        for ext, weight in file_types:
            cumulative += weight
            if r < cumulative:
                return ext
        return ".txt"  # pragma: no cover

    # ------------------------------------------------------------------
    # I/O helpers
    # ------------------------------------------------------------------

    def write_file(self, path: Path, extension: str) -> None:
        ext = extension.lower()
        if ext in _BINARY_CONTENT:
            path.write_bytes(_BINARY_CONTENT[ext])
        elif ext == ".csv":
            path.write_text(self.make_csv_content(self._rng), encoding="utf-8")
        else:
            path.write_text(self.make_txt_content(self._rng), encoding="utf-8")

    def create_files(self, directory: Path, count: int, prefix: str = "") -> None:
        directory.mkdir(parents=True, exist_ok=True)
        width = len(str(count))
        print(f"  Creating {count} files in {directory.name}...")
        for i in range(1, count + 1):
            ext = self.random_extension(self._rng)
            number = str(i).zfill(width)
            name = f"{prefix}{number}{ext}" if prefix else f"{number}{ext}"
            self.write_file(directory / name, ext)
        print(f"  Done: {count} files")

    def generate(
        self,
        output_dir: Path,
        structure: list[tuple[str, int]] | None = None,
        root_count: int | None = None,
    ) -> Path:
        output_dir.mkdir(parents=True, exist_ok=True)
        folders = structure if structure is not None else self.DEFAULT_STRUCTURE
        n_root = root_count if root_count is not None else self.ROOT_COUNT

        print(f"Output: {output_dir}")
        for folder_name, file_count in folders:
            print(f"\n=== {folder_name}: {file_count} files ===")
            self.create_files(output_dir / folder_name, file_count, prefix=f"{folder_name}_")

        print(f"\n=== root: {n_root} files ===")
        self.create_files(output_dir, n_root, prefix="")
        return output_dir

    def generate_deep_nested(self, output_dir: Path, depth: int, files_per_level: int = 1) -> Path:
        output_dir.mkdir(parents=True, exist_ok=True)
        current = output_dir
        for level in range(1, depth + 1):
            current = current / f"level_{level}"
            self.create_files(current, files_per_level, prefix=f"level_{level}_")
        return output_dir

    def generate_long_filename(self, output_dir: Path, max_length: int, extension: str = ".txt") -> Path:
        output_dir.mkdir(parents=True, exist_ok=True)
        stem_length = max(1, max_length - len(extension))
        name = ("a" * stem_length) + extension
        self.write_file(output_dir / name, extension)
        return output_dir


# ---------------------------------------------------------------------------
# Module-level shims (backward compat)
# ---------------------------------------------------------------------------


def get_random_extension() -> str:
    return ArchiveTemplateGenerator.random_extension(_random.Random())


def create_valid_file(file_path: Path, extension: str) -> None:
    ArchiveTemplateGenerator().write_file(file_path, extension)


def create_numbered_files(directory: Path, count: int, prefix: str) -> None:
    ArchiveTemplateGenerator().create_files(directory, count, prefix)


def main() -> None:
    ArchiveTemplateGenerator().generate(OUTPUT_DIR)


if __name__ == "__main__":  # pragma: no cover
    main()
