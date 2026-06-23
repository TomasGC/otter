#!/usr/bin/env python3
"""Integration tests for archive creation — uses format classes with real filesystem."""

import pickle
import subprocess
import tarfile
import zipfile
import zlib
from pathlib import Path

import pytest
from fake_subprocess import FakeResult, FakeSubprocessRunner

from cli.create_test_archives import (
    ArchiveCreator,
    ArchiveFormat,
    RarDockerFormat,
    RpaFormat,
    SevenZipFormat,
    TarFormat,
    TarGzFormat,
    ZipFormat,
)

pytestmark = pytest.mark.integration_mock

FAKE_7Z = "/fake/7z"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def create_minimal_template(template_dir: Path, num_files: int = 5) -> list[str]:
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


def _dirs(tmp_path: Path) -> tuple[Path, Path]:
    tmpl = tmp_path / "template"
    out = tmp_path / "output"
    tmpl.mkdir(exist_ok=True)
    out.mkdir(exist_ok=True)
    return tmpl, out


def read_rpa_index(rpa_file: Path) -> dict:
    """Parse RPA-3.0 archive index. Returns decoded {path: [[offset, size]]}.

    pickle.loads is safe here: archives are created by RpaFormat in same test session.
    """
    data = rpa_file.read_bytes()
    header = data[:34].decode("ascii")
    parts = header.strip().split()
    index_offset = int(parts[1], 16)
    key = int(parts[2], 16)
    index_raw = pickle.loads(zlib.decompress(data[index_offset:]))
    return {path: [[offset ^ key, size ^ key] for offset, size in entries] for path, entries in index_raw.items()}


# ---------------------------------------------------------------------------
# TestCreateRpaArchive — pure Python, no subprocess required
# ---------------------------------------------------------------------------


class TestCreateRpaArchive:
    def test_creates_rpa_file(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl)
        RpaFormat(out, tmpl).create()
        assert (out / "test_archive.rpa").exists()

    def test_rpa_starts_with_magic_bytes(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl)
        RpaFormat(out, tmpl).create()
        assert (out / "test_archive.rpa").read_bytes()[:8] == b"RPA-3.0 "

    def test_rpa_header_is_34_bytes(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl)
        RpaFormat(out, tmpl).create()
        header = (out / "test_archive.rpa").read_bytes()[:34].decode("ascii")
        assert len(header) == 34
        assert header.endswith("\n")

    def test_rpa_header_hex_fields(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl)
        RpaFormat(out, tmpl).create()
        header = (out / "test_archive.rpa").read_bytes()[:34].decode("ascii")
        parts = header.strip().split()
        assert parts[0] == "RPA-3.0"
        assert len(parts[1]) == 16
        assert len(parts[2]) == 8

    def test_rpa_index_is_parseable(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl)
        RpaFormat(out, tmpl).create()
        index = read_rpa_index(out / "test_archive.rpa")
        assert isinstance(index, dict) and len(index) > 0

    def test_rpa_index_contains_all_template_files(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl, num_files=3)
        RpaFormat(out, tmpl).create()
        index = read_rpa_index(out / "test_archive.rpa")
        assert "file_000.txt" in index
        assert "file_001.txt" in index
        assert "file_002.txt" in index
        assert "subdir/nested.txt" in index

    def test_rpa_offsets_are_after_header(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl)
        RpaFormat(out, tmpl).create()
        index = read_rpa_index(out / "test_archive.rpa")
        for path, entries in index.items():
            for offset, _size in entries:
                assert offset >= 34, f"{path}: offset {offset} is before header end"

    def test_rpa_stored_data_matches_template(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl, num_files=2)
        RpaFormat(out, tmpl).create()
        rpa_bytes = (out / "test_archive.rpa").read_bytes()
        index = read_rpa_index(out / "test_archive.rpa")
        for path, entries in index.items():
            offset, size = entries[0]
            end = offset + size
            stored = rpa_bytes[offset:end]
            template_path = tmpl / path.replace("/", "\\")
            if not template_path.exists():
                template_path = tmpl / path
            assert stored == template_path.read_bytes(), f"Data mismatch for {path}"

    def test_rpa_skips_existing_archive(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl)
        sentinel = b"EXISTING_RPA_CONTENT"
        existing = out / "test_archive.rpa"
        existing.write_bytes(sentinel)
        RpaFormat(out, tmpl).create()
        assert existing.read_bytes() == sentinel


# ---------------------------------------------------------------------------
# TestCreateZipArchive — pure Python
# ---------------------------------------------------------------------------


class TestCreateZipArchive:
    def test_creates_zip_file(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl)
        ZipFormat(out, tmpl).create()
        assert (out / "test_archive.zip").exists()

    def test_zip_contains_all_template_files(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl, num_files=3)
        ZipFormat(out, tmpl).create()
        with zipfile.ZipFile(out / "test_archive.zip") as zf:
            names = zf.namelist()
        assert any("file_000.txt" in n for n in names)
        assert any("file_001.txt" in n for n in names)
        assert any("nested.txt" in n for n in names)

    def test_zip_stored_data_matches_template(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl, num_files=2)
        ZipFormat(out, tmpl).create()
        with zipfile.ZipFile(out / "test_archive.zip") as zf:
            for info in zf.infolist():
                stored = zf.read(info.filename)
                file_path = tmpl / info.filename
                if file_path.exists():
                    assert stored == file_path.read_bytes(), f"Data mismatch for {info.filename}"

    def test_zip_skips_existing_archive(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        sentinel = b"EXISTING_ZIP"
        (out / "test_archive.zip").write_bytes(sentinel)
        ZipFormat(out, tmpl).create()
        assert (out / "test_archive.zip").read_bytes() == sentinel


# ---------------------------------------------------------------------------
# TestCreateTarArchive — pure Python
# ---------------------------------------------------------------------------


class TestCreateTarArchive:
    def test_creates_tar_file(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl)
        TarFormat(out, tmpl).create()
        assert (out / "test_archive.tar").exists()

    def test_tar_contains_all_template_files(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl, num_files=3)
        TarFormat(out, tmpl).create()
        with tarfile.open(out / "test_archive.tar") as tf:
            names = tf.getnames()
        assert any("file_000.txt" in n for n in names)
        assert any("nested.txt" in n for n in names)

    def test_tar_stored_data_matches_template(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl, num_files=2)
        TarFormat(out, tmpl).create()
        with tarfile.open(out / "test_archive.tar") as tf:
            for member in tf.getmembers():
                if member.isfile():
                    f = tf.extractfile(member)
                    stored = f.read() if f else b""
                    file_path = tmpl / member.name.lstrip("./")
                    if file_path.exists():
                        assert stored == file_path.read_bytes(), f"Data mismatch for {member.name}"

    def test_tar_skips_existing_archive(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        sentinel = b"EXISTING_TAR"
        (out / "test_archive.tar").write_bytes(sentinel)
        TarFormat(out, tmpl).create()
        assert (out / "test_archive.tar").read_bytes() == sentinel


# ---------------------------------------------------------------------------
# TestCreateTarGzArchive — pure Python
# ---------------------------------------------------------------------------


class TestCreateTarGzArchive:
    def test_creates_tar_gz_file(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl)
        TarGzFormat(out, tmpl).create()
        assert (out / "test_archive.tar.gz").exists()

    def test_tar_gz_is_valid_compressed_archive(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl, num_files=3)
        TarGzFormat(out, tmpl).create()
        with tarfile.open(out / "test_archive.tar.gz", "r:gz") as tf:
            names = tf.getnames()
        assert any("file_000.txt" in n for n in names)
        assert any("nested.txt" in n for n in names)

    def test_tar_gz_smaller_than_tar(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        create_minimal_template(tmpl, num_files=10)
        TarFormat(out, tmpl).create()
        TarGzFormat(out, tmpl).create()
        tar_size = (out / "test_archive.tar").stat().st_size
        tar_gz_size = (out / "test_archive.tar.gz").stat().st_size
        assert tar_gz_size < tar_size

    def test_tar_gz_skips_existing_archive(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        sentinel = b"EXISTING_TARGZ"
        (out / "test_archive.tar.gz").write_bytes(sentinel)
        TarGzFormat(out, tmpl).create()
        assert (out / "test_archive.tar.gz").read_bytes() == sentinel


# ---------------------------------------------------------------------------
# TestCreate7zArchive — subprocess via FakeSubprocessRunner
# ---------------------------------------------------------------------------


class TestCreate7zArchive:
    def _make_runner_creating_file(self, out: Path) -> FakeSubprocessRunner:
        runner = FakeSubprocessRunner()

        def side_effect(cmd, **kwargs):
            runner.calls.append(list(cmd))
            for arg in cmd:
                p = Path(str(arg))
                if p.parent == out and p.name == "test_archive.7z":
                    p.write_bytes(b"FAKE_7Z")
                    break
            return FakeResult(returncode=0)

        runner.run = side_effect
        return runner

    def test_calls_subprocess_once(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        runner = self._make_runner_creating_file(out)
        SevenZipFormat(out, tmpl, runner, FAKE_7Z).create()
        assert len([c for c in runner.calls if c[0] == FAKE_7Z]) == 1

    def test_creates_7z_with_t7z_flag(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        runner = self._make_runner_creating_file(out)
        SevenZipFormat(out, tmpl, runner, FAKE_7Z).create()
        cmd = next((c for c in runner.calls if "test_archive.7z" in " ".join(str(x) for x in c)), None)
        assert cmd is not None and "-t7z" in cmd

    def test_uses_configured_binary(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        runner = self._make_runner_creating_file(out)
        SevenZipFormat(out, tmpl, runner, FAKE_7Z).create()
        assert all(c[0] == FAKE_7Z for c in runner.calls)

    def test_skips_existing_archive(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        runner = FakeSubprocessRunner()
        (out / "test_archive.7z").write_bytes(b"EXISTING")
        SevenZipFormat(out, tmpl, runner, FAKE_7Z).create()
        assert runner.call_count == 0

    def test_returns_none_when_tool_not_found(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        runner = FakeSubprocessRunner()

        def raise_not_found(cmd, **kwargs):
            raise FileNotFoundError("7z not found")

        runner.run = raise_not_found
        assert SevenZipFormat(out, tmpl, runner, FAKE_7Z).create() is None

    def test_catches_called_process_error(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        runner = FakeSubprocessRunner()

        def raise_error(cmd, **kwargs):
            raise subprocess.CalledProcessError(1, cmd, stderr="error")

        runner.run = raise_error
        assert SevenZipFormat(out, tmpl, runner, FAKE_7Z).create() is None


# ---------------------------------------------------------------------------
# TestCreateRarArchiveDocker — subprocess via FakeSubprocessRunner
# ---------------------------------------------------------------------------


class TestCreateRarArchiveDocker:
    def _setup(self, tmp_path: Path) -> tuple[Path, Path, Path]:
        tmpl, out = _dirs(tmp_path)
        dockerfile_dir = tmp_path / "docker"
        dockerfile_dir.mkdir()
        (dockerfile_dir / "rar.Dockerfile").write_text("FROM ubuntu\n")
        return tmpl, out, dockerfile_dir

    def _make_runner_creating_rar(self, out: Path) -> FakeSubprocessRunner:
        runner = FakeSubprocessRunner()

        def side_effect(cmd, **kwargs):
            runner.calls.append(list(cmd))
            if "run" in cmd and cmd[0] == "docker":
                (out / "test_archive.rar").write_bytes(b"FAKE_RAR")
            return FakeResult(returncode=0)

        runner.run = side_effect
        return runner

    def test_builds_docker_image(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = self._make_runner_creating_rar(out)
        RarDockerFormat(out, tmpl, runner, ddir).create()
        assert any("build" in c for c in runner.calls)

    def test_runs_docker_container(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = self._make_runner_creating_rar(out)
        RarDockerFormat(out, tmpl, runner, ddir).create()
        assert any("--rm" in c for c in runner.calls)

    def test_docker_run_uses_volume_mount(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = self._make_runner_creating_rar(out)
        RarDockerFormat(out, tmpl, runner, ddir).create()
        run_calls = [c for c in runner.calls if "--rm" in c]
        assert any("-v" in c for c in run_calls)

    def test_skips_existing_rar_archive(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = FakeSubprocessRunner()
        (out / "test_archive.rar").write_bytes(b"EXISTING")
        RarDockerFormat(out, tmpl, runner, ddir).create()
        assert runner.call_count == 0

    def test_handles_docker_build_failure_gracefully(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = FakeSubprocessRunner()

        def fail_build(cmd, **kwargs):
            runner.calls.append(list(cmd))
            if "build" in cmd:
                raise subprocess.CalledProcessError(1, cmd, stderr="Build failed")
            return FakeResult(returncode=0)

        runner.run = fail_build
        result = RarDockerFormat(out, tmpl, runner, ddir).create()
        assert result is None
        run_calls = [c for c in runner.calls if "--rm" in c]
        assert len(run_calls) == 0

    def test_handles_docker_run_failure_gracefully(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = FakeSubprocessRunner()

        def fail_run(cmd, **kwargs):
            runner.calls.append(list(cmd))
            if "--rm" in cmd:
                raise subprocess.CalledProcessError(1, cmd, stderr="Run failed")
            return FakeResult(returncode=0)

        runner.run = fail_run
        result = RarDockerFormat(out, tmpl, runner, ddir).create()
        assert result is None

    def test_returns_none_when_docker_not_found(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = FakeSubprocessRunner()

        def raise_not_found(cmd, **kwargs):
            raise FileNotFoundError("docker not found")

        runner.run = raise_not_found
        assert RarDockerFormat(out, tmpl, runner, ddir).create() is None


# ---------------------------------------------------------------------------
# TestToDockerPath
# ---------------------------------------------------------------------------


class TestToDockerPath:
    def test_windows_converts_backslashes(self):
        from unittest.mock import patch

        path = Path("C:\\Users\\user\\archives")
        with patch("sys.platform", "win32"):
            result = RarDockerFormat._to_docker_path(path)
        assert "\\" not in result

    def test_linux_returns_path_as_str(self):
        from unittest.mock import patch

        path = Path("/home/user/archives")
        with patch("sys.platform", "linux"):
            result = RarDockerFormat._to_docker_path(path)
        assert result == str(path)


# ---------------------------------------------------------------------------
# TestCreateAll — orchestrator with stub formats
# ---------------------------------------------------------------------------


class _StubFormat(ArchiveFormat):
    def __init__(self, output_dir: Path, template_dir: Path, fmt_name: str, result=None) -> None:
        super().__init__(output_dir, template_dir)
        self._name = fmt_name
        self._result = result
        self.called = False

    @property
    def name(self) -> str:
        return self._name

    def create(self):
        self.called = True
        return self._result


class TestCreateAll:
    def test_calls_create_on_each_format(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        fmt1 = _StubFormat(out, tmpl, "rpa", out / "rpa")
        fmt2 = _StubFormat(out, tmpl, "zip", out / "zip")
        ArchiveCreator([fmt1, fmt2]).create_all()
        assert fmt1.called and fmt2.called

    def test_result_keys_match_format_names(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        results = ArchiveCreator(
            [
                _StubFormat(out, tmpl, "rpa"),
                _StubFormat(out, tmpl, "zip"),
            ]
        ).create_all()
        assert set(results.keys()) == {"rpa", "zip"}

    def test_creates_output_dir_via_main_then_all_formats_called(self, tmp_path):
        tmpl = tmp_path / "template"
        out = tmp_path / "output"
        tmpl.mkdir()
        out.mkdir()
        (tmpl / "file.txt").write_text("x")
        fmt = _StubFormat(out, tmpl, "rpa")
        ArchiveCreator([fmt]).create_all()
        assert fmt.called

    def test_result_includes_none_for_unavailable_format(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        results = ArchiveCreator(
            [
                _StubFormat(out, tmpl, "ok", out / "file"),
                _StubFormat(out, tmpl, "skip", None),
            ]
        ).create_all()
        assert results["ok"] is not None
        assert results["skip"] is None
