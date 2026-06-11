#!/usr/bin/env python3
"""Create test archives in multiple formats (RPA, ZIP, RAR, 7z, TAR, TAR.GZ)."""

import argparse
import pickle
import subprocess
import sys
import zlib
from pathlib import Path
from typing import Optional

from common.file_utils import get_project_root, load_test_settings
from common.subprocess_runner import RealSubprocessRunner, SubprocessRunner

PROJECT_ROOT = get_project_root()
RPA_KEY = 0xDEADBEEF

_DEFAULT_7Z = r"C:\Program Files\7-Zip\7z.exe" if sys.platform == "win32" else "7z"


class ArchiveCreator:
    """Creates test archives in multiple formats.

    All subprocess calls are routed through the injected runner, making
    every method with external-tool dependencies integration-testable with
    FakeSubprocessRunner + real filesystem (tmp_path).

    RPA creation is pure Python — testable without any subprocess at all.
    """

    def __init__(
        self,
        runner: SubprocessRunner,
        output_dir: Path,
        template_dir: Path,
        seven_zip_path: str = _DEFAULT_7Z,
        dockerfile_dir: Optional[Path] = None,
    ) -> None:
        self._runner = runner
        self._output_dir = output_dir
        self._template_dir = template_dir
        self._seven_zip = seven_zip_path
        self._dockerfile_dir = dockerfile_dir or (PROJECT_ROOT / "scripts" / "docker")

    # -------------------------------------------------------------------------
    # RPA — pure Python, no subprocess
    # -------------------------------------------------------------------------

    def create_rpa(self) -> Optional[Path]:
        rpa_file = self._output_dir / "test_archive.rpa"
        if rpa_file.exists():
            print(f"  [SKIP] {rpa_file.name} already exists")
            return rpa_file

        print("\n=== Creating RPA-3.0 archive ===")
        file_list = [
            (str(p.relative_to(self._template_dir)).replace("\\", "/"), p)
            for p in self._template_dir.rglob("*")
            if p.is_file()
        ]

        current_offset = 34  # header length
        index: dict = {}

        with open(rpa_file, "w+b") as rpa:
            rpa.write(b"\x00" * 34)  # placeholder header
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

        size_mb = rpa_file.stat().st_size / (1024 * 1024)
        print(f"  RPA archive created: {rpa_file.name} ({size_mb:.2f} MB)")
        return rpa_file

    # -------------------------------------------------------------------------
    # 7-Zip archives
    # -------------------------------------------------------------------------

    def create_7zip(self) -> list[Path]:
        print("\n=== Creating 7-Zip archives ===")
        template_glob = str(self._template_dir / "*")
        formats = [
            ("zip", ["-tzip"]),
            ("7z", ["-t7z"]),
            ("tar", ["-ttar"]),
            ("tar.gz", ["-ttar", "-mx=9"]),
        ]
        created = []
        for ext, flags in formats:
            out = self._output_dir / f"test_archive.{ext}"
            if out.exists():
                print(f"  [SKIP] {out.name} already exists")
                created.append(out)
                continue
            cmd = [self._seven_zip, "a"] + flags + [str(out), template_glob]
            print(f"  Running: {' '.join(cmd)}")
            try:
                self._runner.run(cmd, check=True, capture_output=True, text=True)
                size_mb = out.stat().st_size / (1024 * 1024)
                print(f"  {ext.upper()} archive created: {out.name} ({size_mb:.2f} MB)")
                created.append(out)
            except subprocess.CalledProcessError as e:
                print(f"  ERROR creating {ext}: {e.stderr}")
        return created

    # -------------------------------------------------------------------------
    # RAR via Docker
    # -------------------------------------------------------------------------

    def create_rar_docker(self) -> Optional[Path]:
        print("\n=== Creating RAR archive (Docker) ===")
        out = self._output_dir / "test_archive.rar"
        if out.exists():
            print(f"  [SKIP] {out.name} already exists")
            return out

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
                    "rar",
                    "a",
                    "-r",
                    "/workspace/output/test_archive.rar",
                    "/workspace/template/",
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            size_mb = out.stat().st_size / (1024 * 1024)
            print(f"  RAR archive created: {out.name} ({size_mb:.2f} MB)")
            return out
        except subprocess.CalledProcessError as e:
            print(f"  ERROR: Docker run failed: {e.stderr}")
            return None

    # -------------------------------------------------------------------------
    # Orchestration
    # -------------------------------------------------------------------------

    def create_all(self, rpa_only: bool = False) -> dict[str, Optional[Path]]:
        self._output_dir.mkdir(parents=True, exist_ok=True)
        results: dict[str, Optional[Path]] = {}
        results["rpa"] = self.create_rpa()
        if not rpa_only:
            sevenz = self.create_7zip()
            for p in sevenz:
                results[p.suffix.lstrip(".")] = p
            results["rar"] = self.create_rar_docker()
        return results

    @staticmethod
    def _to_docker_path(path: Path) -> str:
        if sys.platform == "win32":
            return str(path).replace("\\", "/")  # C:/Users/... for Docker Desktop
        return str(path)


def main(rpa_only: bool = False, output_dir: Optional[Path] = None) -> None:
    settings = load_test_settings()
    template_dir = PROJECT_ROOT / "archives" / "template"
    out = output_dir or (PROJECT_ROOT / settings["test_archives"]["host_path"])
    ArchiveCreator(RealSubprocessRunner(), out, template_dir, _DEFAULT_7Z).create_all(rpa_only=rpa_only)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Create test archives")
    parser.add_argument("--rpa-only", action="store_true")
    parser.add_argument("--output-dir", type=Path, default=None)
    args = parser.parse_args()
    main(rpa_only=args.rpa_only, output_dir=args.output_dir)
