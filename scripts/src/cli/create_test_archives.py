#!/usr/bin/env python3
"""
Create test archives in multiple formats (RPA, ZIP, RAR, 7z, TAR, TAR.GZ).
Uses 7-Zip for standard formats and custom RPA-3.0 generator.
Configuration from test-settings.json.
"""

import argparse
import json
import os
import pickle
import subprocess
import sys
import zlib
from pathlib import Path


PROJECT_ROOT = Path(__file__).parent.parent.parent.parent


def load_test_settings() -> dict:
    """Load test settings from test-settings.json."""
    settings_path = PROJECT_ROOT / "app" / "src" / "androidTest" / "assets" / "test-settings.json"
    with open(settings_path, "r", encoding="utf-8") as f:
        return json.load(f)


# Load settings
TEST_SETTINGS = load_test_settings()

# Paths
TEMPLATE_DIR = PROJECT_ROOT / "archives" / "template"
OUTPUT_DIR = PROJECT_ROOT / TEST_SETTINGS["test_archives"]["host_path"]
SEVEN_ZIP = r"C:\Program Files\7-Zip\7z.exe"

# RPA-3.0 parameters
RPA_KEY = 0xDEADBEEF  # XOR obfuscation key


def create_rpa_archive():
    """Create RPA-3.0 archive from template directory."""
    print("\n=== Creating RPA-3.0 archive ===")

    rpa_file = OUTPUT_DIR / "test_archive.rpa"

    # Skip if already exists
    if rpa_file.exists():
        file_size = rpa_file.stat().st_size / (1024 * 1024)
        print(f"  [SKIP] RPA archive already exists: {rpa_file.name} ({file_size:.2f} MB) - skipping")
        return

    # Collect all files with their paths relative to template
    file_list = []
    for file_path in TEMPLATE_DIR.rglob("*"):
        if file_path.is_file():
            relative_path = file_path.relative_to(TEMPLATE_DIR)
            # RPA uses forward slashes
            rpa_path = str(relative_path).replace("\\", "/")
            file_list.append((rpa_path, file_path))

    print(f"Found {len(file_list)} files to archive")

    # Write RPA archive
    # RPA-3.0 format: [Header 34 bytes][File data][Compressed index]
    # Header must be written first as offsets in index are relative to file start

    # Pre-calculate header (34 bytes: "RPA-3.0 " + 16 hex + " " + 8 hex + "\n")
    HEADER_SIZE = 34

    with open(rpa_file, "wb") as rpa:
        # Reserve space for header (will write it at the end when we know index_offset)
        rpa.write(b'\x00' * HEADER_SIZE)

        # Write files sequentially and build index
        index = {}
        current_offset = HEADER_SIZE  # Start after header

        for rpa_path, file_path in file_list:
            file_size = file_path.stat().st_size
            file_data = file_path.read_bytes()

            # Write file data
            rpa.write(file_data)

            # Store in index: filename -> [[offset, size]]
            # Apply XOR obfuscation
            obfuscated_offset = current_offset ^ RPA_KEY
            obfuscated_size = file_size ^ RPA_KEY
            index[rpa_path] = [[obfuscated_offset, obfuscated_size]]

            current_offset += file_size

            if (len(index) % 1000) == 0:
                print(f"  Written {len(index)} files...")

        # Serialize index with pickle (protocol 2 for maximum compatibility)
        # Protocol 2 works with minimal pickle parsers (no complex opcodes)
        index_bytes = pickle.dumps(index, protocol=2)

        # Compress index with zlib
        compressed_index = zlib.compress(index_bytes, level=9)

        # Write compressed index
        index_offset = current_offset
        rpa.write(compressed_index)

        print(f"  Index size: {len(compressed_index)} bytes (compressed from {len(index_bytes)})")

        # Now write the header at the beginning
        rpa.seek(0)
        header = f"RPA-3.0 {index_offset:016X} {RPA_KEY:08X}\n".encode("ascii")
        rpa.write(header)

    final_size = rpa_file.stat().st_size / (1024 * 1024)
    print(f"  RPA archive created: {rpa_file.name} ({final_size:.2f} MB)")


def create_rar_archive_docker():
    """Create RAR archive using Docker with official RAR CLI."""
    print("\n=== Creating RAR archive (Docker) ===")

    output_file = OUTPUT_DIR / "test_archive.rar"

    # Skip if already exists
    if output_file.exists():
        file_size = output_file.stat().st_size / (1024 * 1024)
        print(f"  [SKIP] RAR archive already exists: {output_file.name} ({file_size:.2f} MB) - skipping")
        return

    # Build Docker image if not exists
    dockerfile_path = PROJECT_ROOT / "scripts" / "docker" / "rar.Dockerfile"
    print(f"  Building Docker image from {dockerfile_path}...")

    try:
        subprocess.run(
            ["docker", "build", "-f", str(dockerfile_path), "-t", "rar-builder", "."],
            cwd=PROJECT_ROOT,  # Root du projet
            check=True,
            capture_output=True,
            text=True,
        )
        print("  Docker image built successfully")
    except subprocess.CalledProcessError as e:
        print(f"  ERROR: Failed to build Docker image")
        print(f"  {e.stderr}")
        return

    # Create RAR archive using Docker
    print(f"  Creating RAR archive via Docker...")

    # Convert Windows path to /c/... format for Docker
    project_abs = str(PROJECT_ROOT.absolute()).replace("\\", "/").replace("C:", "/c")

    try:
        subprocess.run(
            [
                "docker", "run", "--rm",
                "-v", f"{project_abs}:/workspace",
                "rar-builder",
                "/workspace/archives/test_archive.rar",
                "/workspace/archives/template/",
            ],
            check=True,
            capture_output=True,
            text=True,
        )

        file_size = output_file.stat().st_size / (1024 * 1024)
        print(f"  RAR archive created: {output_file.name} ({file_size:.2f} MB)")
    except subprocess.CalledProcessError as e:
        print(f"  ERROR: Failed to create RAR archive")
        print(f"  {e.stderr}")


def create_7zip_archives():
    """Create ZIP, 7z, TAR, TAR.GZ archives using 7-Zip."""
    formats = [
        ("ZIP", "zip", "-tzip"),
        ("7z", "7z", "-t7z"),
        ("TAR", "tar", "-ttar"),
        ("TAR.GZ", "tar.gz", "-ttar -mx=9"),
    ]

    for format_name, extension, format_flag in formats:
        print(f"\n=== Creating {format_name} archive ===")

        output_file = OUTPUT_DIR / f"test_archive.{extension}"

        # Skip if already exists
        if output_file.exists():
            file_size = output_file.stat().st_size / (1024 * 1024)
            print(f"  [SKIP] {format_name} archive already exists: {output_file.name} ({file_size:.2f} MB) - skipping")
            continue

        # 7-Zip command: 7z a -t<format> output.ext input_folder\*
        cmd = [
            SEVEN_ZIP,
            "a",  # Add to archive
            format_flag,  # Format type
            str(output_file),
            str(TEMPLATE_DIR / "*"),
        ]

        # For TAR.GZ, use -ttar with compression
        if extension == "tar.gz":
            cmd = [
                SEVEN_ZIP,
                "a",
                "-ttar",
                "-mx=9",  # Max compression
                str(output_file),
                str(TEMPLATE_DIR / "*"),
            ]

        print(f"  Running: {' '.join(cmd)}")

        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                check=True,
            )

            file_size = output_file.stat().st_size / (1024 * 1024)
            print(f"  {format_name} archive created: {output_file.name} ({file_size:.2f} MB)")
        except subprocess.CalledProcessError as e:
            print(f"  ERROR: Failed to create {format_name} archive")
            print(f"  {e.stderr}")


def main(rpa_only: bool = False, output_dir: Path = None):
    """Create all test archives."""
    global OUTPUT_DIR
    if output_dir is not None:
        OUTPUT_DIR = output_dir

    print("=" * 60)
    print("Test Archive Creator")
    print("=" * 60)

    # Generate template if doesn't exist or is empty
    if not TEMPLATE_DIR.exists() or not any(TEMPLATE_DIR.iterdir()):
        print(f"\nTemplate directory not found or empty: {TEMPLATE_DIR}")
        print("Generating template files automatically...")

        generate_script = Path(__file__).parent / "generate_archive_template.py"
        if not generate_script.exists():
            print(f"ERROR: generate_archive_template.py not found: {generate_script}")
            print("This script should exist at: scripts/generate_archive_template.py")
            return

        try:
            result = subprocess.run(
                [sys.executable, str(generate_script)],
                check=True,
                capture_output=True,
                text=True,
            )
            print(result.stdout)
            print("[SKIP] Template generation complete\n")
        except subprocess.CalledProcessError as e:
            print(f"ERROR: Failed to generate template files")
            print(e.stderr)
            return

    # Verify 7-Zip exists (not needed for --rpa-only)
    if not rpa_only and not Path(SEVEN_ZIP).exists():
        print(f"\nERROR: 7-Zip not found: {SEVEN_ZIP}")
        print("Install 7-Zip from https://www.7-zip.org/")
        return

    # Create output directory
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"\nTemplate: {TEMPLATE_DIR}")
    print(f"Output: {OUTPUT_DIR}")

    # Count files in template
    file_count = sum(1 for _ in TEMPLATE_DIR.rglob("*") if _.is_file())
    print(f"Files in template: {file_count}")

    # Create archives
    create_rpa_archive()
    if not rpa_only:
        create_7zip_archives()
        create_rar_archive_docker()

    print("\n" + "=" * 60)
    print("Archive creation complete!")
    print("=" * 60)

    # List created archives
    print("\nCreated archives:")
    for archive in OUTPUT_DIR.glob("test_archive.*"):
        size = archive.stat().st_size / (1024 * 1024)
        print(f"  - {archive.name} ({size:.2f} MB)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Create test archives")
    parser.add_argument("--rpa-only", action="store_true", help="Only create RPA archive (no 7z, no Docker)")
    parser.add_argument("--output-dir", type=Path, default=None, help="Output directory for archives (default: archives/)")
    args = parser.parse_args()
    main(rpa_only=args.rpa_only, output_dir=args.output_dir)
