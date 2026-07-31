"""Multi-volume / split archive fixtures for RAR and 7z.

RAR: uses Docker (rar CLI not cross-platform available).
7z:  uses the local 7z binary.

Volume size is kept small (50 KB) so even a minimal template directory
always produces at least two volumes.
"""

import subprocess
import sys
from pathlib import Path
from typing import Optional

from cli.archive_scenarios.base import ArchiveFormat, ArchiveFormatScenario
from common.file_utils import get_project_root
from common.subprocess_runner import SubprocessRunner

PROJECT_ROOT = get_project_root()

_DEFAULT_7Z = r"C:\Program Files\7-Zip\7z.exe" if sys.platform == "win32" else "7z"

# Small enough that a typical template directory (several KB of files) spans >= 2 volumes.
_VOLUME_SIZE_KB = 50


class SplitSevenZipFormat(ArchiveFormat):
    """7z multi-volume archive: produces archive.7z.001, .002, ..."""

    def __init__(
        self,
        output_dir: Path,
        template_dir: Path,
        runner: SubprocessRunner,
        seven_zip_path: str = _DEFAULT_7Z,
        file_prefix: str = "split_7z",
    ) -> None:
        super().__init__(output_dir, template_dir, file_prefix)
        self._runner = runner
        self._seven_zip = seven_zip_path

    @property
    def name(self) -> str:
        return "7z_split"

    def create(self) -> Optional[Path]:
        first_volume = self._output_dir / f"{self._file_prefix}.7z.001"
        if first_volume.exists():
            print(f"  [SKIP] {first_volume.name} already exists")
            return first_volume

        print("\n=== Creating split 7z archive ===")
        base_name = self._output_dir / f"{self._file_prefix}.7z"
        cmd = [
            self._seven_zip,
            "a",
            f"-v{_VOLUME_SIZE_KB}k",
            str(base_name),
            str(self._template_dir / "*"),
        ]
        print(f"  Running: {' '.join(cmd)}")
        try:
            self._runner.run(cmd, check=True, capture_output=True, text=True)
            if not first_volume.exists():
                print("  [SKIP] 7z did not produce split volumes (archive too small?)")
                return None
            volumes = sorted(self._output_dir.glob(f"{self._file_prefix}.7z.*"))
            print(f"  Split 7z created: {len(volumes)} volume(s) — first: {first_volume.name}")
            return first_volume
        except FileNotFoundError:
            print("  [SKIP] 7z not available")
            return None
        except subprocess.CalledProcessError as e:
            print(f"  ERROR creating split 7z: {e.stderr}")
            return None


class SplitRarDockerFormat(ArchiveFormat):
    """RAR multi-volume archive via Docker: produces archive.part1.rar, .part2.rar, ..."""

    def __init__(
        self,
        output_dir: Path,
        template_dir: Path,
        runner: SubprocessRunner,
        dockerfile_dir: Optional[Path] = None,
        file_prefix: str = "split_rar",
    ) -> None:
        super().__init__(output_dir, template_dir, file_prefix)
        self._runner = runner
        self._dockerfile_dir = dockerfile_dir or (PROJECT_ROOT / "scripts" / "docker")

    @property
    def name(self) -> str:
        return "rar_split"

    def create(self) -> Optional[Path]:
        first_volume = self._output_dir / f"{self._file_prefix}.part1.rar"
        if first_volume.exists():
            print(f"  [SKIP] {first_volume.name} already exists")
            return first_volume

        if not any(p.is_file() for p in self._template_dir.rglob("*")):
            print("  [SKIP] No files in template directory for split RAR")
            return None

        print("\n=== Creating split RAR archive (Docker) ===")
        dockerfile = self._dockerfile_dir / "rar.Dockerfile"
        try:
            self._runner.run(
                ["docker", "build", "-f", str(dockerfile), "-t", "rar-builder", "."],
                cwd=PROJECT_ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
        except FileNotFoundError:
            print("  [SKIP] Docker not available")
            return None
        except subprocess.CalledProcessError as e:
            print(f"  ERROR: Failed to build Docker image: {e.stderr}")
            return None

        template_docker = self._to_docker_path(self._template_dir)
        output_docker = self._to_docker_path(self._output_dir)
        archive_path = f"/workspace/output/{self._file_prefix}.rar"
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
                    # -v<n>k: split into volumes of n kilobytes
                    # -ep1:   store relative paths, not full /workspace/template/... paths
                    f"-v{_VOLUME_SIZE_KB}k",
                    archive_path,
                    "/workspace/template/",
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            if not first_volume.exists():
                print("  [SKIP] RAR split archive not produced (archive too small?)")
                return None
            volumes = sorted(self._output_dir.glob(f"{self._file_prefix}.part*.rar"))
            print(f"  Split RAR created: {len(volumes)} volume(s) — first: {first_volume.name}")
            return first_volume
        except subprocess.CalledProcessError as e:
            print(f"  ERROR: Docker run failed: {e.stderr}")
            return None

    @staticmethod
    def _to_docker_path(path: Path) -> str:
        if sys.platform == "win32":
            return str(path).replace("\\", "/")
        return str(path)


class SplitArchives(ArchiveFormatScenario):
    """Multi-volume archives for RAR (Docker) and 7z."""

    def __init__(self, runner: SubprocessRunner, output_dir: Path, template_dir: Path) -> None:
        super().__init__(
            [
                SplitSevenZipFormat(output_dir, template_dir, runner),
                SplitRarDockerFormat(output_dir, template_dir, runner),
            ]
        )
