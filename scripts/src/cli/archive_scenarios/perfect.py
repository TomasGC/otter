#!/usr/bin/env python3
"""Perfect (happy-path) archive fixtures in multiple formats (RPA, ZIP, RAR, 7z, TAR, TAR.GZ)."""

import pickle
import subprocess
import sys
import tarfile
import zipfile
import zlib
from pathlib import Path
from typing import Optional

from cli.archive_scenarios.base import ArchiveFormat, ArchiveFormatScenario
from common.file_utils import get_project_root
from common.subprocess_runner import SubprocessRunner

PROJECT_ROOT = get_project_root()
RPA_KEY = 0xDEADBEEF

_DEFAULT_7Z = r"C:\Program Files\7-Zip\7z.exe" if sys.platform == "win32" else "7z"


# ---------------------------------------------------------------------------
# Pure-Python formats (no subprocess)
# ---------------------------------------------------------------------------


class RpaFormat(ArchiveFormat):
    @property
    def name(self) -> str:
        return "rpa"

    def create(self) -> Optional[Path]:
        out = self._output_path("rpa")
        if (skip := self._skip_if_exists(out)) is not None:
            return skip

        print("\n=== Creating RPA-3.0 archive ===")
        file_list = [
            (str(p.relative_to(self._template_dir)).replace("\\", "/"), p)
            for p in self._template_dir.rglob("*")
            if p.is_file()
        ]

        current_offset = 34  # header length
        index: dict = {}

        with open(out, "w+b") as rpa:
            rpa.write(b"\x00" * 34)
            for rpa_path, file_path in file_list:
                data = file_path.read_bytes()
                rpa.write(data)
                index[rpa_path] = [[current_offset ^ RPA_KEY, len(data) ^ RPA_KEY]]
                current_offset += len(data)
                if len(index) % 1000 == 0:
                    print(f"  Written {len(index)} files...")

            index_offset = current_offset
            compressed = zlib.compress(pickle.dumps(index, protocol=2), level=9)
            rpa.write(compressed)

            rpa.seek(0)
            rpa.write(f"RPA-3.0 {index_offset:016X} {RPA_KEY:08X}\n".encode("ascii"))

        size_mb = out.stat().st_size / (1024 * 1024)
        print(f"  RPA archive created: {out.name} ({size_mb:.2f} MB)")
        return out


class ZipFormat(ArchiveFormat):
    @property
    def name(self) -> str:
        return "zip"

    def create(self) -> Optional[Path]:
        out = self._output_path("zip")
        if (skip := self._skip_if_exists(out)) is not None:
            return skip

        print("\n=== Creating ZIP archive ===")
        with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            for file_path in self._template_dir.rglob("*"):
                if file_path.is_file():
                    zf.write(file_path, file_path.relative_to(self._template_dir))

        size_mb = out.stat().st_size / (1024 * 1024)
        print(f"  ZIP archive created: {out.name} ({size_mb:.2f} MB)")
        return out


class TarFormat(ArchiveFormat):
    @property
    def name(self) -> str:
        return "tar"

    def create(self) -> Optional[Path]:
        out = self._output_path("tar")
        if (skip := self._skip_if_exists(out)) is not None:
            return skip

        print("\n=== Creating TAR archive ===")
        with tarfile.open(out, "w") as tf:
            for child in sorted(self._template_dir.iterdir()):
                tf.add(child, arcname=child.name)

        size_mb = out.stat().st_size / (1024 * 1024)
        print(f"  TAR archive created: {out.name} ({size_mb:.2f} MB)")
        return out


class TarGzFormat(ArchiveFormat):
    @property
    def name(self) -> str:
        return "tar.gz"

    def create(self) -> Optional[Path]:
        out = self._output_path("tar.gz")
        if (skip := self._skip_if_exists(out)) is not None:
            return skip

        print("\n=== Creating TAR.GZ archive ===")
        with tarfile.open(out, "w:gz") as tf:
            for child in sorted(self._template_dir.iterdir()):
                tf.add(child, arcname=child.name)

        size_mb = out.stat().st_size / (1024 * 1024)
        print(f"  TAR.GZ archive created: {out.name} ({size_mb:.2f} MB)")
        return out


class TarBz2Format(ArchiveFormat):
    @property
    def name(self) -> str:
        return "tar.bz2"

    def create(self) -> Optional[Path]:
        out = self._output_path("tar.bz2")
        if (skip := self._skip_if_exists(out)) is not None:
            return skip

        print("\n=== Creating TAR.BZ2 archive ===")
        with tarfile.open(out, "w:bz2") as tf:
            for child in sorted(self._template_dir.iterdir()):
                tf.add(child, arcname=child.name)

        size_mb = out.stat().st_size / (1024 * 1024)
        print(f"  TAR.BZ2 archive created: {out.name} ({size_mb:.2f} MB)")
        return out


class GzipSingleFileFormat(ArchiveFormat):
    """Creates a .gz archive from the first file found in the template directory."""

    @property
    def name(self) -> str:
        return "gz"

    def create(self) -> Optional[Path]:
        import gzip as gzip_module

        out = self._output_path("gz")
        if (skip := self._skip_if_exists(out)) is not None:
            return skip

        files = [p for p in self._template_dir.rglob("*") if p.is_file()]
        if not files:
            print("  [SKIP] No files in template directory for GZIP")
            return None

        source_file = files[0]
        print(f"\n=== Creating GZIP archive from {source_file.name} ===")
        with source_file.open("rb") as f_in:
            with gzip_module.open(out, "wb") as f_out:
                f_out.write(f_in.read())

        size_mb = out.stat().st_size / (1024 * 1024)
        print(f"  GZIP archive created: {out.name} ({size_mb:.2f} MB)")
        return out


# ---------------------------------------------------------------------------
# External-tool formats (subprocess, self-handle unavailability)
# ---------------------------------------------------------------------------


class SevenZipFormat(ArchiveFormat):
    def __init__(
        self,
        output_dir: Path,
        template_dir: Path,
        runner: SubprocessRunner,
        seven_zip_path: str = _DEFAULT_7Z,
        file_prefix: str = "test_archive",
    ) -> None:
        super().__init__(output_dir, template_dir, file_prefix)
        self._runner = runner
        self._seven_zip = seven_zip_path

    @property
    def name(self) -> str:
        return "7z"

    def create(self) -> Optional[Path]:
        out = self._output_path("7z")
        if (skip := self._skip_if_exists(out)) is not None:
            return skip

        print("\n=== Creating 7z archive ===")
        cmd = [self._seven_zip, "a", "-t7z", str(out), str(self._template_dir / "*")]
        print(f"  Running: {' '.join(cmd)}")
        try:
            self._runner.run(cmd, check=True, capture_output=True, text=True)
            size_mb = out.stat().st_size / (1024 * 1024)
            print(f"  7z archive created: {out.name} ({size_mb:.2f} MB)")
            return out
        except FileNotFoundError:
            print("  [SKIP] 7z not available")
            return None
        except subprocess.CalledProcessError as e:
            print(f"  ERROR creating 7z: {e.stderr}")
            return None


class RarDockerFormat(ArchiveFormat):
    def __init__(
        self,
        output_dir: Path,
        template_dir: Path,
        runner: SubprocessRunner,
        dockerfile_dir: Optional[Path] = None,
        file_prefix: str = "test_archive",
    ) -> None:
        super().__init__(output_dir, template_dir, file_prefix)
        self._runner = runner
        self._dockerfile_dir = dockerfile_dir or (PROJECT_ROOT / "scripts" / "docker")

    @property
    def name(self) -> str:
        return "rar"

    def create(self) -> Optional[Path]:
        out = self._output_path("rar")
        if (skip := self._skip_if_exists(out)) is not None:
            return skip

        if not any(p.is_file() for p in self._template_dir.rglob("*")):
            print("  [SKIP] No files in template directory for RAR")
            return None

        print("\n=== Creating RAR archive (Docker) ===")
        dockerfile = self._dockerfile_dir / "rar.Dockerfile"
        print(f"  Building Docker image from {dockerfile}...")
        try:
            self._runner.run(
                ["docker", "build", "-f", str(dockerfile), "-t", "rar-builder", "."],
                cwd=PROJECT_ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
            print("  Docker image built successfully")
        except FileNotFoundError:
            print("  [SKIP] Docker not available")
            return None
        except subprocess.CalledProcessError as e:
            print(f"  ERROR: Failed to build Docker image: {e.stderr}")
            return None

        template_docker = self._to_docker_path(self._template_dir)
        output_docker = self._to_docker_path(self._output_dir)
        print("  Creating RAR archive via Docker...")
        try:
            self._runner.run(
                [
                    "docker",
                    "run",
                    "--rm",
                    "-v",
                    f"{template_docker}:/workspace/template:ro",
                    "-v",
                    f"{output_docker}:/workspace/output",
                    "rar-builder",
                    f"/workspace/output/{self._file_prefix}.rar",
                    "/workspace/template/",
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            if not out.exists():
                print("  [SKIP] RAR archive was not created by Docker run")
                return None
            size_mb = out.stat().st_size / (1024 * 1024)
            print(f"  RAR archive created: {out.name} ({size_mb:.2f} MB)")
            return out
        except subprocess.CalledProcessError as e:
            print(f"  ERROR: Docker run failed: {e.stderr}")
            return None

    @staticmethod
    def _to_docker_path(path: Path) -> str:
        if sys.platform == "win32":
            return str(path).replace("\\", "/")
        return str(path)


# ---------------------------------------------------------------------------
# Scenario
# ---------------------------------------------------------------------------


def default_formats(
    runner: SubprocessRunner,
    output_dir: Path,
    template_dir: Path,
    file_prefix: str = "test_archive",
) -> list[ArchiveFormat]:
    return [
        RpaFormat(output_dir, template_dir, file_prefix),
        ZipFormat(output_dir, template_dir, file_prefix),
        TarFormat(output_dir, template_dir, file_prefix),
        TarGzFormat(output_dir, template_dir, file_prefix),
        TarBz2Format(output_dir, template_dir, file_prefix),
        GzipSingleFileFormat(output_dir, template_dir, file_prefix),
        SevenZipFormat(output_dir, template_dir, runner, file_prefix=file_prefix),
        RarDockerFormat(output_dir, template_dir, runner, file_prefix=file_prefix),
    ]


class PerfectArchives(ArchiveFormatScenario):
    """Happy-path archives: one valid archive per format, from the real template tree."""

    def __init__(self, runner: SubprocessRunner, output_dir: Path, template_dir: Path) -> None:
        super().__init__(default_formats(runner, output_dir, template_dir))
