#!/usr/bin/env python3
"""Unit tests for ArchiveCreator."""

import pickle
import sys
import zlib
from pathlib import Path

import pytest

from cli.create_test_archives import ArchiveCreator
from fake_subprocess import FakeSubprocessRunner

FAKE_7Z = "/fake/7z"

def make_creator(tmp_path, runner=None, seven_zip=FAKE_7Z):
    template = tmp_path / "template"
    output = tmp_path / "output"
    template.mkdir(exist_ok=True)
    output.mkdir(exist_ok=True)
    return ArchiveCreator(
        runner or FakeSubprocessRunner(),
        output,
        template,
        seven_zip_path=seven_zip,
        dockerfile_dir=tmp_path / "docker",
    ), template, output

def make_template(template_dir: Path, num_files: int = 3) -> None:
    for i in range(num_files):
        (template_dir / f"file_{i:03d}.txt").write_text(f"content {i}\n", encoding="utf-8")
    sub = template_dir / "subdir"
    sub.mkdir(exist_ok=True)
    (sub / "nested.txt").write_text("nested\n", encoding="utf-8")

# ---------------------------------------------------------------------------
# RPA — pure Python, no subprocess needed
# ---------------------------------------------------------------------------

class TestCreateRpa:
    def test_creates_rpa_file(self, tmp_path):
        c, tmpl, out = make_creator(tmp_path)
        make_template(tmpl)
        c.create_rpa()
        assert (out / "test_archive.rpa").exists()

    def test_starts_with_magic_bytes(self, tmp_path):
        c, tmpl, out = make_creator(tmp_path)
        make_template(tmpl)
        c.create_rpa()
        assert (out / "test_archive.rpa").read_bytes()[:8] == b"RPA-3.0 "

    def test_header_is_34_bytes(self, tmp_path):
        c, tmpl, out = make_creator(tmp_path)
        make_template(tmpl)
        c.create_rpa()
        header = (out / "test_archive.rpa").read_bytes()[:34].decode("ascii")
        assert len(header) == 34 and header.endswith("\n")

    def test_index_is_parseable(self, tmp_path):
        c, tmpl, out = make_creator(tmp_path)
        make_template(tmpl)
        c.create_rpa()
        data = (out / "test_archive.rpa").read_bytes()
        parts = data[:34].decode("ascii").strip().split()
        index_offset = int(parts[1], 16)
        key = int(parts[2], 16)
        # Safe: data was written by create_rpa() moments ago in the same test process.
        raw = pickle.loads(zlib.decompress(data[index_offset:]))  # noqa: S301
        assert isinstance(raw, dict) and len(raw) > 0

    def test_skips_when_file_already_exists(self, tmp_path):
        c, tmpl, out = make_creator(tmp_path)
        make_template(tmpl)
        sentinel = b"EXISTING_RPA"
        existing = out / "test_archive.rpa"
        existing.write_bytes(sentinel)
        c.create_rpa()
        assert existing.read_bytes() == sentinel

    def test_no_subprocess_calls_made(self, tmp_path):
        runner = FakeSubprocessRunner()
        c, tmpl, out = make_creator(tmp_path, runner=runner)
        make_template(tmpl)
        c.create_rpa()
        assert runner.call_count == 0

# ---------------------------------------------------------------------------
# 7z archives — subprocess mocked
# ---------------------------------------------------------------------------

class TestCreate7zip:
    def _run(self, tmp_path):
        runner = FakeSubprocessRunner()
        c, tmpl, out = make_creator(tmp_path, runner=runner)

        def side_effect(cmd, **kwargs):
            runner.calls.append(list(cmd))
            for arg in cmd:
                p = Path(arg)
                if p.parent == out and p.name.startswith("test_archive."):
                    p.write_bytes(b"FAKE")
                    break
            from fake_subprocess import FakeResult
            return FakeResult(returncode=0)

        runner.run = side_effect
        c.create_7zip()
        return out, runner

    def test_calls_subprocess_four_times(self, tmp_path):
        _, runner = self._run(tmp_path)
        assert runner.call_count == 4

    def test_uses_configured_7z_binary(self, tmp_path):
        _, runner = self._run(tmp_path)
        for cmd in runner.calls:
            assert cmd[0] == FAKE_7Z

    def test_zip_uses_tzip_flag(self, tmp_path):
        _, runner = self._run(tmp_path)
        zip_cmd = next((c for c in runner.calls if "test_archive.zip" in " ".join(c)), None)
        assert zip_cmd and "-tzip" in zip_cmd

    def test_7z_uses_t7z_flag(self, tmp_path):
        _, runner = self._run(tmp_path)
        sevenz_cmd = next((c for c in runner.calls if "test_archive.7z" in " ".join(c)), None)
        assert sevenz_cmd and "-t7z" in sevenz_cmd

    def test_tar_gz_uses_ttar_and_mx9(self, tmp_path):
        _, runner = self._run(tmp_path)
        tar_cmd = next((c for c in runner.calls if "test_archive.tar.gz" in " ".join(c)), None)
        assert tar_cmd and "-ttar" in tar_cmd and "-mx=9" in tar_cmd

    def test_skips_existing_archives(self, tmp_path):
        runner = FakeSubprocessRunner()
        c, tmpl, out = make_creator(tmp_path, runner=runner)
        for ext in ["zip", "7z", "tar", "tar.gz"]:
            (out / f"test_archive.{ext}").write_bytes(b"EXISTING")
        runner.calls.clear()
        c.create_7zip()
        assert runner.call_count == 0

# ---------------------------------------------------------------------------
# RAR via Docker — subprocess mocked
# ---------------------------------------------------------------------------

class TestCreateRarDocker:
    def _run(self, tmp_path):
        runner = FakeSubprocessRunner()
        c, tmpl, out = make_creator(tmp_path, runner=runner)
        (tmp_path / "docker").mkdir(exist_ok=True)
        (tmp_path / "docker" / "rar.Dockerfile").write_text("FROM ubuntu\n")

        def side_effect(cmd, **kwargs):
            runner.calls.append(list(cmd))
            if "run" in cmd and "docker" in cmd[0]:
                (out / "test_archive.rar").write_bytes(b"FAKE_RAR")
            from fake_subprocess import FakeResult
            return FakeResult(returncode=0)

        runner.run = side_effect
        c.create_rar_docker()
        return out, runner

    def test_calls_docker_build(self, tmp_path):
        _, runner = self._run(tmp_path)
        assert runner.called_with("docker", "build")

    def test_calls_docker_run(self, tmp_path):
        _, runner = self._run(tmp_path)
        assert runner.called_with("docker", "run")

    def test_docker_run_uses_rm_flag(self, tmp_path):
        _, runner = self._run(tmp_path)
        run_calls = [c for c in runner.calls if "run" in c and "docker" in c[0]]
        assert any("--rm" in c for c in run_calls)

    def test_docker_run_uses_volume_mounts(self, tmp_path):
        _, runner = self._run(tmp_path)
        run_calls = [c for c in runner.calls if "run" in c and "docker" in c[0]]
        assert any("-v" in c for c in run_calls)

    def test_skips_existing_rar(self, tmp_path):
        runner = FakeSubprocessRunner()
        c, _, out = make_creator(tmp_path, runner=runner)
        (out / "test_archive.rar").write_bytes(b"EXISTING")
        c.create_rar_docker()
        assert runner.call_count == 0

    def test_returns_none_on_build_failure(self, tmp_path):
        runner = FakeSubprocessRunner()
        c, tmpl, out = make_creator(tmp_path, runner=runner)
        (tmp_path / "docker").mkdir(exist_ok=True)
        (tmp_path / "docker" / "rar.Dockerfile").write_text("FROM ubuntu\n")

        def fail_build(cmd, **kwargs):
            runner.calls.append(list(cmd))
            if "build" in cmd:
                raise subprocess.CalledProcessError(1, cmd, "Build failed")
            from fake_subprocess import FakeResult
            return FakeResult(returncode=0)

        import subprocess
        runner.run = fail_build
        result = c.create_rar_docker()
        assert result is None

    def test_does_not_run_docker_when_build_fails(self, tmp_path):
        import subprocess
        runner = FakeSubprocessRunner()
        c, _, out = make_creator(tmp_path, runner=runner)
        (tmp_path / "docker").mkdir(exist_ok=True)
        (tmp_path / "docker" / "rar.Dockerfile").write_text("FROM ubuntu\n")

        def fail_build(cmd, **kwargs):
            runner.calls.append(list(cmd))
            if "build" in cmd:
                raise subprocess.CalledProcessError(1, cmd)
            from fake_subprocess import FakeResult
            return FakeResult(returncode=0)

        runner.run = fail_build
        c.create_rar_docker()
        run_calls = [c for c in runner.calls if "run" in c]
        assert len(run_calls) == 0

# ---------------------------------------------------------------------------
# create_all orchestration
# ---------------------------------------------------------------------------

class TestCreateAll:
    def test_rpa_only_skips_subprocess(self, tmp_path):
        runner = FakeSubprocessRunner()
        c, tmpl, out = make_creator(tmp_path, runner=runner)
        (tmpl / "file.txt").write_text("x")
        c.create_all(rpa_only=True)
        assert runner.call_count == 0

    def test_creates_output_dir_when_missing(self, tmp_path):
        runner = FakeSubprocessRunner()
        out = tmp_path / "new_output"
        tmpl = tmp_path / "template"
        tmpl.mkdir()
        (tmpl / "file.txt").write_text("x")
        c = ArchiveCreator(runner, out, tmpl, FAKE_7Z)
        c.create_all(rpa_only=True)
        assert out.is_dir()

    def test_rpa_present_in_results(self, tmp_path):
        runner = FakeSubprocessRunner()
        c, tmpl, out = make_creator(tmp_path, runner=runner)
        (tmpl / "file.txt").write_text("x")
        results = c.create_all(rpa_only=True)
        assert "rpa" in results and results["rpa"] is not None
