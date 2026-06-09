#!/usr/bin/env python3
"""
Generate test archive structure with real, valid files.
Structure: 1000 files at root + 6 folders with varying file counts.
"""

import os
import random
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.parent.parent.parent

# Target directory: project_root/archives/template/
OUTPUT_DIR = PROJECT_ROOT / "archives" / "template"

# File type distribution (weights sum to 100)
FILE_TYPES = [
    (".txt", 25),
    (".csv", 15),
    (".pdf", 10),
    (".jpg", 15),
    (".png", 10),
    (".gif", 5),
    (".webp", 5),  # WEBP image statique
    (".webm", 10), # WEBM vidéo
    (".mp4", 5),
]


def get_random_extension():
    """Get random extension based on weighted distribution."""
    total_weight = sum(weight for _, weight in FILE_TYPES)
    rand = random.randint(0, total_weight - 1)

    cumulative = 0
    for ext, weight in FILE_TYPES:
        cumulative += weight
        if rand < cumulative:
            return ext

    return ".txt"  # Fallback


def create_txt_file(path):
    """Create a small text file."""
    lines = random.randint(1, 5)
    content = "\n".join([f"Line {i} with random data: {random.randint(1000, 9999)}" for i in range(lines)])
    path.write_text(content, encoding='utf-8')


def create_csv_file(path):
    """Create a small CSV file."""
    rows = random.randint(1, 5)
    content = "Name,Value,Date\n"
    for i in range(rows):
        content += f"Item{i},{random.randint(1, 999)},2024-01-{random.randint(1, 28):02d}\n"
    path.write_text(content, encoding='utf-8')


def create_minimal_pdf(path):
    """Create minimal valid PDF (177 bytes)."""
    pdf_content = b"""%PDF-1.4
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
    path.write_bytes(pdf_content)


def create_minimal_jpg(path):
    """Create minimal 1x1 JPEG (134 bytes) - RED pixel."""
    jpeg_bytes = bytes([
        0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x01, 0x00, 0x48,
        0x00, 0x48, 0x00, 0x00, 0xFF, 0xDB, 0x00, 0x43, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xC0, 0x00, 0x0B, 0x08, 0x00,
        0x01, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00, 0xFF, 0xC4, 0x00, 0x14, 0x00, 0x01, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0xFF, 0xC4, 0x00,
        0x14, 0x10, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0xFF, 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x37, 0xFF, 0xD9
    ])
    path.write_bytes(jpeg_bytes)


def create_minimal_png(path):
    """Create minimal 1x1 PNG (67 bytes) - RED pixel."""
    png_bytes = bytes([
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53,
        0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, 0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00,
        0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xDD, 0x8D, 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
        0x44, 0xAE, 0x42, 0x60, 0x82
    ])
    path.write_bytes(png_bytes)


def create_minimal_gif(path):
    """Create minimal 1x1 GIF (35 bytes) - WHITE pixel."""
    gif_bytes = bytes([
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80, 0x00, 0x00, 0xFF, 0xFF, 0xFF,
        0x00, 0x00, 0x00, 0x21, 0xF9, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00,
        0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
    ])
    path.write_bytes(gif_bytes)


def create_minimal_webp(path):
    """Create minimal 1x1 WEBP image (32 bytes) - Lossless format."""
    webp_bytes = bytes([
        0x52, 0x49, 0x46, 0x46, 0x1A, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x4C,
        0x0D, 0x00, 0x00, 0x00, 0x2F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x41, 0x9C, 0x01, 0x4A, 0x00, 0x9D
    ])
    path.write_bytes(webp_bytes)


def create_minimal_mp4(path):
    """Create minimal MP4 with 1 frame (~40 bytes)."""
    mp4_bytes = bytes([
        # ftyp box
        0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6D, 0x70, 0x34, 0x32, 0x00, 0x00, 0x00, 0x00,
        0x6D, 0x70, 0x34, 0x32, 0x69, 0x73, 0x6F, 0x6D,
        # mdat box (minimal)
        0x00, 0x00, 0x00, 0x08, 0x6D, 0x64, 0x61, 0x74
    ])
    path.write_bytes(mp4_bytes)


def create_minimal_webm(path):
    """Create minimal WEBM video (Matroska container with VP8/VP9)."""
    webm_bytes = bytes([
        # EBML header
        0x1A, 0x45, 0xDF, 0xA3, 0x9F, 0x42, 0x86, 0x81, 0x01, 0x42, 0xF7, 0x81, 0x01, 0x42, 0xF2, 0x81,
        0x04, 0x42, 0xF3, 0x81, 0x08, 0x42, 0x82, 0x84, 0x77, 0x65, 0x62, 0x6D, 0x42, 0x87, 0x81, 0x02,
        0x42, 0x85, 0x81, 0x02,
        # Segment
        0x18, 0x53, 0x80, 0x67, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1F
    ])
    path.write_bytes(webm_bytes)


def create_valid_file(file_path, extension):
    """Create a valid file based on extension."""
    creators = {
        ".txt": create_txt_file,
        ".csv": create_csv_file,
        ".pdf": create_minimal_pdf,
        ".jpg": create_minimal_jpg,
        ".jpeg": create_minimal_jpg,
        ".png": create_minimal_png,
        ".gif": create_minimal_gif,
        ".webp": create_minimal_webp,  # WEBP image statique
        ".webm": create_minimal_webm,  # WEBM vidéo
        ".mp4": create_minimal_mp4,
    }

    creator = creators.get(extension.lower(), create_txt_file)
    creator(file_path)


def create_numbered_files(directory, count, prefix):
    """
    Create N files with deterministic zero-padded names.

    Name format: {prefix}_{zero_padded_number}.{ext}
    - Root files (no prefix): 0001.ext, 0002.ext, ..., 1000.ext
    - Folder files: folder_300_001.ext, folder_800_001.ext, etc.

    Zero-padding width = len(str(count)).
    """
    width = len(str(count))
    print(f"Creating {count} files in {directory} (prefix='{prefix}', width={width})...")

    for i in range(1, count + 1):
        ext = get_random_extension()
        number = str(i).zfill(width)
        filename = f"{prefix}{number}{ext}" if prefix else f"{number}{ext}"
        file_path = directory / filename
        create_valid_file(file_path, ext)

        if i % 5000 == 0:
            print(f"  {i} / {count} files created...")

    print(f"  Completed: {count} files created")


def main():
    """Generate test archive structure.

    Structure:
      root/
        folder_300/        ← 300 files: folder_300_001.ext … folder_300_300.ext
        folder_800/        ← 800 files: folder_800_001.ext … folder_800_800.ext
        folder_2000/       ← 2000 files: folder_2000_0001.ext … folder_2000_2000.ext
        folder_10000/      ← 10000 files: folder_10000_00001.ext … folder_10000_10000.ext
        folder_50000/      ← 50000 files: folder_50000_00001.ext … folder_50000_50000.ext
        folder_200000/     ← 200000 files: folder_200000_000001.ext … folder_200000_200000.ext
        0001.ext           ← 1000 root files: 0001.ext … 1000.ext
        …
        1000.ext
    """
    print("=" * 60)
    print("Test Archive Generator")
    print("=" * 60)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"\nOutput directory: {OUTPUT_DIR}")

    # Folders first (so they sort before root files in the archive)
    folders = [
        ("folder_30",    30),
        ("folder_80",    80),
        ("folder_300",   300),
        ("folder_500",   500),
        ("folder_1000",  1000),
        ("folder_2000",  2000),
    ]

    for folder_name, file_count in folders:
        folder_path = OUTPUT_DIR / folder_name
        folder_path.mkdir(exist_ok=True)
        print(f"\n=== {folder_name}: {file_count} files ===")
        create_numbered_files(folder_path, file_count, prefix=f"{folder_name}_")

    # Root files: 001.ext … 100.ext (no prefix)
    print(f"\n=== root: 100 files ===")
    create_numbered_files(OUTPUT_DIR, 100, prefix="")

    print("\n" + "=" * 60)
    print("Generation complete!")
    print("=" * 60)
    print("Total files: 100 + 30 + 80 + 300 + 500 + 1000 + 2000 = 4010")

    total_size = sum(f.stat().st_size for f in OUTPUT_DIR.rglob('*') if f.is_file())
    print(f"Total size: {total_size / (1024 * 1024):.2f} MB")


if __name__ == "__main__":
    main()
