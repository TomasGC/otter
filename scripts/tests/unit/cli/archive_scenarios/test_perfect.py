#!/usr/bin/env python3
"""Unit tests for the Perfect (happy-path) archive format classes."""

import pickle
import subprocess
import tarfile
import zipfile
import zlib
from pathlib import Path

from fake_subprocess import FakeResult, FakeSubprocessRunner

from cli.archive_scenarios.perfect import (
    GzipSingleFileFormat,
    RarDockerFormat,
    RpaFormat,
    SevenZipFormat,
    TarBz2Format,
    TarFormat,
    TarGzFormat,
    ZipFormat,
)

FAKE_7Z = "/fake/7z"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def make_template(template_dir: Path, num_files: int = 3) -> None:
    for i in range(num_files):
        (template_dir / f"file_{i:03d}.txt").write_text(f"content {i}\n", encoding="utf-8")
    sub = template_dir / "subdir"
    sub.mkdir(exist_ok=True)
    (sub / "nested.txt").write_text("nested\n", encoding="utf-8")


def _dirs(tmp_path: Path) -> tuple[Path, Path]:
    tmpl = tmp_path / "template"
    out = tmp_path / "output"
    tmpl.mkdir()
    out.mkdir()
    return tmpl, out


# ---------------------------------------------------------------------------
# RPA — pure Python
# ---------------------------------------------------------------------------


class TestCreateRpa:
    def test_creates_rpa_file(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        make_template(tmpl)
        RpaFormat(out, tmpl).create()
        assert (out / "test_archive.rpa").exists()

    def test_starts_with_magic_bytes(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        make_template(tmpl)
        RpaFormat(out, tmpl).create()
        assert (out / "test_archive.rpa").read_bytes()[:8] == b"RPA-3.0 "

    def test_header_is_34_bytes(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        make_template(tmpl)
        RpaFormat(out, tmpl).create()
        header = (out / "test_archive.rpa").read_bytes()[:34].decode("ascii")
        assert len(header) == 34 and header.endswith("\n")

    def test_index_is_parseable(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        make_template(tmpl)
        RpaFormat(out, tmpl).create()
        data = (out / "test_archive.rpa").read_bytes()
        parts = data[:34].decode("ascii").strip().split()
        index_offset = int(parts[1], 16)
        # Safe: data written by create() moments ago in the same test process.
        raw = pickle.loads(zlib.decompress(data[index_offset:]))
        assert isinstance(raw, dict) and len(raw) > 0

    def test_skips_when_file_already_exists(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        make_template(tmpl)
        sentinel = b"EXISTING_RPA"
        existing = out / "test_archive.rpa"
        existing.write_bytes(sentinel)
        RpaFormat(out, tmpl).create()
        assert existing.read_bytes() == sentinel

    def test_name_is_rpa(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        assert RpaFormat(out, tmpl).name == "rpa"


# ---------------------------------------------------------------------------
# ZIP — pure Python
# ---------------------------------------------------------------------------


class TestCreateZip:
    def test_creates_zip_file(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        make_template(tmpl)
        ZipFormat(out, tmpl).create()
        assert (out / "test_archive.zip").exists()

    def test_zip_contains_template_files(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        make_template(tmpl)
        ZipFormat(out, tmpl).create()
        with zipfile.ZipFile(out / "test_archive.zip") as zf:
            names = zf.namelist()
        assert any("file_000.txt" in n for n in names)
        assert any("nested.txt" in n for n in names)

    def test_skips_when_file_already_exists(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        sentinel = b"EXISTING_ZIP"
        existing = out / "test_archive.zip"
        existing.write_bytes(sentinel)
        ZipFormat(out, tmpl).create()
        assert existing.read_bytes() == sentinel

    def test_name_is_zip(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        assert ZipFormat(out, tmpl).name == "zip"


# ---------------------------------------------------------------------------
# TAR — pure Python
# ---------------------------------------------------------------------------


class TestCreateTar:
    def test_creates_tar_file(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        make_template(tmpl)
        TarFormat(out, tmpl).create()
        assert (out / "test_archive.tar").exists()

    def test_tar_contains_template_files(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        make_template(tmpl)
        TarFormat(out, tmpl).create()
        with tarfile.open(out / "test_archive.tar") as tf:
            names = tf.getnames()
        assert any("file_000.txt" in n for n in names)

    def test_skips_when_file_already_exists(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        sentinel = b"EXISTING_TAR"
        existing = out / "test_archive.tar"
        existing.write_bytes(sentinel)
        TarFormat(out, tmpl).create()
        assert existing.read_bytes() == sentinel

    def test_name_is_tar(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        assert TarFormat(out, tmpl).name == "tar"


# ---------------------------------------------------------------------------
# TAR.GZ — pure Python
# ---------------------------------------------------------------------------


class TestCreateTarGz:
    def test_creates_tar_gz_file(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        make_template(tmpl)
        TarGzFormat(out, tmpl).create()
        assert (out / "test_archive.tar.gz").exists()

    def test_tar_gz_is_valid_gzip(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        make_template(tmpl)
        TarGzFormat(out, tmpl).create()
        with tarfile.open(out / "test_archive.tar.gz", "r:gz") as tf:
            names = tf.getnames()
        assert any("file_000.txt" in n for n in names)

    def test_skips_when_file_already_exists(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        sentinel = b"EXISTING_TARGZ"
        existing = out / "test_archive.tar.gz"
        existing.write_bytes(sentinel)
        TarGzFormat(out, tmpl).create()
        assert existing.read_bytes() == sentinel

    def test_name_is_tar_gz(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        assert TarGzFormat(out, tmpl).name == "tar.gz"


# ---------------------------------------------------------------------------
# 7z — subprocess, self-handles unavailability
# ---------------------------------------------------------------------------


class TestCreate7z:
    def _make_runner_creating_file(self, out: Path) -> FakeSubprocessRunner:
        runner = FakeSubprocessRunner()

        def side_effect(cmd, **kwargs):
            runner.calls.append(list(cmd))
            for arg in cmd:
                p = Path(arg)
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
        assert runner.call_count == 1

    def test_uses_configured_7z_binary(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        runner = self._make_runner_creating_file(out)
        SevenZipFormat(out, tmpl, runner, FAKE_7Z).create()
        assert runner.calls[0][0] == FAKE_7Z

    def test_uses_t7z_flag(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        runner = self._make_runner_creating_file(out)
        SevenZipFormat(out, tmpl, runner, FAKE_7Z).create()
        assert "-t7z" in runner.calls[0]

    def test_skips_existing_archive(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        runner = FakeSubprocessRunner()
        (out / "test_archive.7z").write_bytes(b"EXISTING")
        SevenZipFormat(out, tmpl, runner, FAKE_7Z).create()
        assert runner.call_count == 0

    def test_returns_none_when_7z_not_found(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        runner = FakeSubprocessRunner()

        def raise_not_found(cmd, **kwargs):
            raise FileNotFoundError("7z not found")

        runner.run = raise_not_found
        result = SevenZipFormat(out, tmpl, runner, FAKE_7Z).create()
        assert result is None

    def test_returns_none_on_subprocess_error(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        runner = FakeSubprocessRunner()

        def raise_error(cmd, **kwargs):
            raise subprocess.CalledProcessError(1, cmd, stderr="error")

        runner.run = raise_error
        result = SevenZipFormat(out, tmpl, runner, FAKE_7Z).create()
        assert result is None

    def test_name_is_7z(self, tmp_path):
        tmpl, out = _dirs(tmp_path)
        assert SevenZipFormat(out, tmpl, FakeSubprocessRunner(), FAKE_7Z).name == "7z"


# ---------------------------------------------------------------------------
# RAR via Docker — subprocess, self-handles unavailability
# ---------------------------------------------------------------------------


class TestCreateRarDocker:
    def _setup(self, tmp_path: Path) -> tuple[Path, Path, Path]:
        tmpl, out = _dirs(tmp_path)
        dockerfile_dir = tmp_path / "docker"
        dockerfile_dir.mkdir()
        (dockerfile_dir / "rar.Dockerfile").write_text("FROM ubuntu\n")
        (tmpl / "file.txt").write_text("content", encoding="utf-8")
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

    def test_calls_docker_build(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = self._make_runner_creating_rar(out)
        RarDockerFormat(out, tmpl, runner, ddir).create()
        assert runner.called_with("docker", "build")

    def test_calls_docker_run(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = self._make_runner_creating_rar(out)
        RarDockerFormat(out, tmpl, runner, ddir).create()
        assert runner.called_with("docker", "run")

    def test_docker_run_uses_rm_flag(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = self._make_runner_creating_rar(out)
        RarDockerFormat(out, tmpl, runner, ddir).create()
        run_calls = [c for c in runner.calls if "run" in c and c[0] == "docker"]
        assert any("--rm" in c for c in run_calls)

    def test_docker_run_uses_volume_mounts(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = self._make_runner_creating_rar(out)
        RarDockerFormat(out, tmpl, runner, ddir).create()
        run_calls = [c for c in runner.calls if "run" in c and c[0] == "docker"]
        assert any("-v" in c for c in run_calls)

    def test_skips_existing_rar(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = FakeSubprocessRunner()
        (out / "test_archive.rar").write_bytes(b"EXISTING")
        RarDockerFormat(out, tmpl, runner, ddir).create()
        assert runner.call_count == 0

    def test_returns_none_on_build_failure(self, tmp_path):
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

    def test_does_not_run_docker_when_build_fails(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = FakeSubprocessRunner()

        def fail_build(cmd, **kwargs):
            runner.calls.append(list(cmd))
            if "build" in cmd:
                raise subprocess.CalledProcessError(1, cmd)
            return FakeResult(returncode=0)

        runner.run = fail_build
        RarDockerFormat(out, tmpl, runner, ddir).create()
        run_calls = [c for c in runner.calls if "run" in c]
        assert len(run_calls) == 0

    def test_returns_none_when_docker_not_found(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        runner = FakeSubprocessRunner()

        def raise_not_found(cmd, **kwargs):
            raise FileNotFoundError("docker not found")

        runner.run = raise_not_found
        result = RarDockerFormat(out, tmpl, runner, ddir).create()
        assert result is None

    def test_name_is_rar(self, tmp_path):
        tmpl, out, ddir = self._setup(tmp_path)
        assert RarDockerFormat(out, tmpl, FakeSubprocessRunner(), ddir).name == "rar"

    def test_returns_none_when_template_has_no_files(self, tmp_path):
        # The rar CLI refuses to create an archive from zero files (exit 10,
        # "WARNING: No files") -- discovered by actually running the real pipeline
        # against an empty template dir. Must short-circuit before invoking Docker,
        # same pattern as GzipSingleFileFormat's "nothing to compress" check.
        tmpl, out = _dirs(tmp_path)
        runner = FakeSubprocessRunner()
        result = RarDockerFormat(out, tmpl, runner, tmp_path / "docker").create()
        assert result is None
        assert runner.call_count == 0


# ---------------------------------------------------------------------------
# TarBz2Format
# ---------------------------------------------------------------------------


def test_tar_bz2_format_creates_archive(tmp_path):
    template_dir = tmp_path / "template"
    template_dir.mkdir()
    (template_dir / "hello.txt").write_text("hello")
    output_dir = tmp_path / "output"
    output_dir.mkdir()

    fmt = TarBz2Format(output_dir, template_dir)
    result = fmt.create()

    assert result is not None
    assert result.exists()
    assert result.name == "test_archive.tar.bz2"


def test_tar_bz2_format_skips_if_exists(tmp_path):
    template_dir = tmp_path / "template"
    template_dir.mkdir()
    (template_dir / "f.txt").write_text("x")
    output_dir = tmp_path / "output"
    output_dir.mkdir()
    existing = output_dir / "test_archive.tar.bz2"
    existing.write_bytes(b"existing")

    fmt = TarBz2Format(output_dir, template_dir)
    result = fmt.create()

    assert result == existing
    assert existing.read_bytes() == b"existing"


# ---------------------------------------------------------------------------
# GzipSingleFileFormat
# ---------------------------------------------------------------------------


def test_gzip_format_creates_archive(tmp_path):
    template_dir = tmp_path / "template"
    template_dir.mkdir()
    (template_dir / "hello.txt").write_text("hello world")
    output_dir = tmp_path / "output"
    output_dir.mkdir()

    fmt = GzipSingleFileFormat(output_dir, template_dir)
    result = fmt.create()

    assert result is not None
    assert result.exists()
    assert result.name == "test_archive.gz"


def test_gzip_format_skips_when_no_files(tmp_path):
    template_dir = tmp_path / "template"
    template_dir.mkdir()
    output_dir = tmp_path / "output"
    output_dir.mkdir()

    fmt = GzipSingleFileFormat(output_dir, template_dir)
    result = fmt.create()

    assert result is None


def test_gzip_format_skips_if_exists(tmp_path):
    template_dir = tmp_path / "template"
    template_dir.mkdir()
    (template_dir / "f.txt").write_text("x")
    output_dir = tmp_path / "output"
    output_dir.mkdir()
    existing = output_dir / "test_archive.gz"
    existing.write_bytes(b"existing")

    fmt = GzipSingleFileFormat(output_dir, template_dir)
    result = fmt.create()

    assert result == existing
    assert existing.read_bytes() == b"existing"
