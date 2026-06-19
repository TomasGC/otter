#!/usr/bin/env python3
"""Integration-real tests for archive creation via Docker (real tools, real subprocess)."""

import shutil
import subprocess
import sys
import uuid
import zipfile
from pathlib import Path

import pytest

DOCKERFILE = Path(__file__).parent.parent / "docker" / "archive-tools.Dockerfile"
_TEMP_ROOT = Path(__file__).parents[4] / "temp" / "docker-tests"


@pytest.fixture
def docker_tmp():
    """Workspace under repo temp/ instead of pytest tmp_path.

    pytest creates tmp_path with private ACLs on Windows, which the Docker
    daemon cannot mount as a volume ("Access is denied").
    """
    workspace = _TEMP_ROOT / uuid.uuid4().hex
    workspace.mkdir(parents=True)
    yield workspace
    shutil.rmtree(workspace, ignore_errors=True)


def _create_minimal_template(template_dir: Path, num_files: int = 5) -> list:
    template_dir.mkdir(parents=True, exist_ok=True)
    names = []
    for i in range(num_files):
        name = f"file_{i:03d}.txt"
        (template_dir / name).write_text(f"Content of file {i}\n", encoding="utf-8")
        names.append(name)
    subdir = template_dir / "subdir"
    subdir.mkdir(exist_ok=True)
    (subdir / "nested.txt").write_text("Nested content\n", encoding="utf-8")
    names.append("subdir/nested.txt")
    return names


def _is_docker_available() -> bool:
    try:
        result = subprocess.run(["docker", "info"], capture_output=True, timeout=5)
        return result.returncode == 0
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return False


def _to_docker_path(path: Path) -> str:
    if sys.platform == "win32":
        return str(path).replace("\\", "/")
    return str(path)


@pytest.mark.integration_real
@pytest.mark.slow
@pytest.mark.skipif(not _is_docker_available(), reason="Docker not available")
class TestDockerArchiveCreation:
    IMAGE = "otter-archive-tools-test"

    @classmethod
    def setup_class(cls):
        subprocess.run(
            ["docker", "build", "-f", str(DOCKERFILE), "-t", cls.IMAGE, str(DOCKERFILE.parent)],
            check=True,
            capture_output=True,
        )

    def _docker_run(self, template_dir: Path, output_dir: Path, cmd: list):
        subprocess.run(
            [
                "docker",
                "run",
                "--rm",
                "-v",
                f"{_to_docker_path(template_dir)}:/workspace/template:ro",
                "-v",
                f"{_to_docker_path(output_dir)}:/workspace/output",
                self.IMAGE,
            ]
            + cmd,
            check=True,
            capture_output=True,
        )

    def test_dockerfile_builds(self):
        """setup_class succeeded → image built without errors."""

    def test_zip_archive_created(self, docker_tmp):
        template_dir = docker_tmp / "template"
        output_dir = docker_tmp / "output"
        _create_minimal_template(template_dir, num_files=3)
        output_dir.mkdir()
        self._docker_run(
            template_dir,
            output_dir,
            [
                "7z",
                "a",
                "-tzip",
                "/workspace/output/test.zip",
                "/workspace/template/*",
            ],
        )
        assert (output_dir / "test.zip").exists()
        assert (output_dir / "test.zip").stat().st_size > 0

    def test_zip_is_extractable(self, docker_tmp):
        template_dir = docker_tmp / "template"
        output_dir = docker_tmp / "output"
        _create_minimal_template(template_dir, num_files=2)
        output_dir.mkdir()
        self._docker_run(
            template_dir,
            output_dir,
            [
                "7z",
                "a",
                "-tzip",
                "/workspace/output/archive.zip",
                "/workspace/template/*",
            ],
        )
        with zipfile.ZipFile(output_dir / "archive.zip") as zf:
            assert len(zf.namelist()) > 0

    def test_zip_contains_expected_files(self, docker_tmp):
        template_dir = docker_tmp / "template"
        output_dir = docker_tmp / "output"
        template_dir.mkdir()
        output_dir.mkdir()
        expected = {"alpha.txt", "beta.csv", "gamma.txt"}
        for name in expected:
            (template_dir / name).write_text(f"content {name}")
        self._docker_run(
            template_dir,
            output_dir,
            [
                "7z",
                "a",
                "-tzip",
                "/workspace/output/archive.zip",
                "/workspace/template/*",
            ],
        )
        with zipfile.ZipFile(output_dir / "archive.zip") as zf:
            archived = {Path(n).name for n in zf.namelist()}
            assert expected.issubset(archived)

    def test_7z_archive_created(self, docker_tmp):
        template_dir = docker_tmp / "template"
        output_dir = docker_tmp / "output"
        _create_minimal_template(template_dir, num_files=2)
        output_dir.mkdir()
        self._docker_run(
            template_dir,
            output_dir,
            [
                "7z",
                "a",
                "-t7z",
                "/workspace/output/test.7z",
                "/workspace/template/*",
            ],
        )
        assert (output_dir / "test.7z").exists()
        assert (output_dir / "test.7z").stat().st_size > 0

    def test_tar_gz_archive_created(self, docker_tmp):
        template_dir = docker_tmp / "template"
        output_dir = docker_tmp / "output"
        _create_minimal_template(template_dir, num_files=2)
        output_dir.mkdir()
        self._docker_run(
            template_dir,
            output_dir,
            [
                "tar",
                "-czf",
                "/workspace/output/test.tar.gz",
                "-C",
                "/workspace/template",
                ".",
            ],
        )
        assert (output_dir / "test.tar.gz").exists()
        assert (output_dir / "test.tar.gz").stat().st_size > 0
