#!/usr/bin/env python3
"""Integration tests for archive creation — uses ArchiveCreator class directly."""

import pickle
import subprocess
import zlib
from pathlib import Path

from fake_subprocess import FakeSubprocessRunner

from cli.create_test_archives import ArchiveCreator

FAKE_7Z = "/fake/7z"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def create_minimal_template(template_dir: Path, num_files: int = 5) -> list:
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


def read_rpa_index(rpa_file: Path) -> dict:
    """Parse RPA-3.0 archive index. Returns decoded {path: [[offset, size]]}.

    pickle.loads is safe here: we only ever parse archives created by
    ArchiveCreator.create_rpa() in the same test session (tmp_path).
    """
    data = rpa_file.read_bytes()
    header = data[:34].decode("ascii")
    parts = header.strip().split()
    index_offset = int(parts[1], 16)
    key = int(parts[2], 16)
    # Safe: pickle data comes from an archive created by ArchiveCreator in the same test.
    index_raw = pickle.loads(zlib.decompress(data[index_offset:]))
    return {path: [[offset ^ key, size ^ key] for offset, size in entries] for path, entries in index_raw.items()}


def make_creator(tmp_path, runner=None):
    template_dir = tmp_path / "template"
    output_dir = tmp_path / "output"
    output_dir.mkdir(parents=True, exist_ok=True)
    return ArchiveCreator(runner or FakeSubprocessRunner(), output_dir, template_dir, FAKE_7Z), template_dir, output_dir


# ---------------------------------------------------------------------------
# TestCreateRpaArchive — pure Python, no subprocess required
# ---------------------------------------------------------------------------


class TestCreateRpaArchive:
    def test_creates_rpa_file(self, tmp_path):
        creator, template_dir, output_dir = make_creator(tmp_path)
        create_minimal_template(template_dir)
        creator.create_rpa()
        assert (output_dir / "test_archive.rpa").exists()

    def test_rpa_starts_with_magic_bytes(self, tmp_path):
        creator, template_dir, output_dir = make_creator(tmp_path)
        create_minimal_template(template_dir)
        creator.create_rpa()
        header_bytes = (output_dir / "test_archive.rpa").read_bytes()[:8]
        assert header_bytes == b"RPA-3.0 "

    def test_rpa_header_is_34_bytes(self, tmp_path):
        creator, template_dir, output_dir = make_creator(tmp_path)
        create_minimal_template(template_dir)
        creator.create_rpa()
        header = (output_dir / "test_archive.rpa").read_bytes()[:34].decode("ascii")
        assert len(header) == 34
        assert header.endswith("\n")

    def test_rpa_header_hex_fields(self, tmp_path):
        creator, template_dir, output_dir = make_creator(tmp_path)
        create_minimal_template(template_dir)
        creator.create_rpa()
        header = (output_dir / "test_archive.rpa").read_bytes()[:34].decode("ascii")
        parts = header.strip().split()
        assert parts[0] == "RPA-3.0"
        assert len(parts[1]) == 16
        assert len(parts[2]) == 8

    def test_rpa_index_is_parseable(self, tmp_path):
        creator, template_dir, output_dir = make_creator(tmp_path)
        create_minimal_template(template_dir)
        creator.create_rpa()
        index = read_rpa_index(output_dir / "test_archive.rpa")
        assert isinstance(index, dict)
        assert len(index) > 0

    def test_rpa_index_contains_all_template_files(self, tmp_path):
        creator, template_dir, output_dir = make_creator(tmp_path)
        create_minimal_template(template_dir, num_files=3)
        creator.create_rpa()
        index = read_rpa_index(output_dir / "test_archive.rpa")
        assert "file_000.txt" in index
        assert "file_001.txt" in index
        assert "file_002.txt" in index
        assert "subdir/nested.txt" in index

    def test_rpa_offsets_are_after_header(self, tmp_path):
        creator, template_dir, output_dir = make_creator(tmp_path)
        create_minimal_template(template_dir)
        creator.create_rpa()
        index = read_rpa_index(output_dir / "test_archive.rpa")
        for path, entries in index.items():
            for offset, _size in entries:
                assert offset >= 34, f"{path}: offset {offset} is before header end"

    def test_rpa_stored_data_matches_template(self, tmp_path):
        creator, template_dir, output_dir = make_creator(tmp_path)
        create_minimal_template(template_dir, num_files=2)
        creator.create_rpa()
        rpa_bytes = (output_dir / "test_archive.rpa").read_bytes()
        index = read_rpa_index(output_dir / "test_archive.rpa")
        for path, entries in index.items():
            offset, size = entries[0]
            end = offset + size
            stored = rpa_bytes[offset:end]
            template_path = template_dir / path.replace("/", "\\")
            if not template_path.exists():
                template_path = template_dir / path
            original = template_path.read_bytes()
            assert stored == original, f"Data mismatch for {path}"

    def test_rpa_skips_existing_archive(self, tmp_path):
        creator, template_dir, output_dir = make_creator(tmp_path)
        create_minimal_template(template_dir)
        sentinel = b"EXISTING_RPA_CONTENT"
        existing = output_dir / "test_archive.rpa"
        existing.write_bytes(sentinel)
        creator.create_rpa()
        assert existing.read_bytes() == sentinel


# ---------------------------------------------------------------------------
# TestCreate7zipArchives — FakeSubprocessRunner
# ---------------------------------------------------------------------------


class TestCreate7zipArchives:
    def _make_runner_and_creator(self, tmp_path):
        runner = FakeSubprocessRunner()
        creator, template_dir, output_dir = make_creator(tmp_path, runner)
        template_dir.mkdir(parents=True, exist_ok=True)

        # Patch FakeSubprocessRunner.run to create output files
        original_run = runner.run

        def patched_run(cmd, **kwargs):
            for arg in cmd:
                p = Path(str(arg))
                if p.parent == output_dir and p.name.startswith("test_archive."):
                    p.write_bytes(b"FAKE")
                    break
            return original_run(cmd, **kwargs)

        runner.run = patched_run
        # Queue 4 success results (zip, 7z, tar, tar.gz)
        for _ in range(4):
            runner.add_run(returncode=0)
        return creator, output_dir, runner

    def test_calls_subprocess_four_times(self, tmp_path):
        creator, _, runner = self._make_runner_and_creator(tmp_path)
        creator.create_7zip()
        assert len([c for c in runner.calls if c[0] == FAKE_7Z]) == 4

    def test_creates_zip_with_tzip_flag(self, tmp_path):
        creator, _, runner = self._make_runner_and_creator(tmp_path)
        creator.create_7zip()
        zip_cmd = next((c for c in runner.calls if "test_archive.zip" in " ".join(str(x) for x in c)), None)
        assert zip_cmd is not None
        assert "-tzip" in zip_cmd

    def test_creates_7z_with_t7z_flag(self, tmp_path):
        creator, _, runner = self._make_runner_and_creator(tmp_path)
        creator.create_7zip()
        sevenz_cmd = next((c for c in runner.calls if "test_archive.7z" in " ".join(str(x) for x in c)), None)
        assert sevenz_cmd is not None
        assert "-t7z" in sevenz_cmd

    def test_creates_tar_gz_with_mx9_flag(self, tmp_path):
        creator, _, runner = self._make_runner_and_creator(tmp_path)
        creator.create_7zip()
        tar_gz_cmd = next((c for c in runner.calls if "test_archive.tar.gz" in " ".join(str(x) for x in c)), None)
        assert tar_gz_cmd is not None
        assert "-ttar" in tar_gz_cmd
        assert "-mx=9" in tar_gz_cmd

    def test_skips_all_existing_archives(self, tmp_path):
        creator, output_dir, runner = self._make_runner_and_creator(tmp_path)
        for ext in ["zip", "7z", "tar", "tar.gz"]:
            (output_dir / f"test_archive.{ext}").write_bytes(b"EXISTING")
        creator.create_7zip()
        assert len([c for c in runner.calls if c[0] == FAKE_7Z]) == 0

    def test_output_paths_in_commands(self, tmp_path):
        creator, _, runner = self._make_runner_and_creator(tmp_path)
        creator.create_7zip()
        all_args = " ".join(str(arg) for cmd in runner.calls for arg in cmd)
        assert "test_archive.zip" in all_args
        assert "test_archive.7z" in all_args
        assert "test_archive.tar" in all_args

    def test_uses_configured_7z_binary(self, tmp_path):
        creator, _, runner = self._make_runner_and_creator(tmp_path)
        creator.create_7zip()
        for cmd in [c for c in runner.calls if c[0] == FAKE_7Z]:
            assert cmd[0] == FAKE_7Z

    def test_catches_called_process_error_per_format(self, tmp_path):
        runner = FakeSubprocessRunner()
        creator, template_dir, output_dir = make_creator(tmp_path, runner)
        template_dir.mkdir(parents=True, exist_ok=True)
        for _ in range(4):
            runner.add_run(returncode=1)
        created = creator.create_7zip()
        assert created == []


# ---------------------------------------------------------------------------
# TestCreateRarArchiveDocker — FakeSubprocessRunner
# ---------------------------------------------------------------------------


class TestCreateRarArchiveDocker:
    def _make_runner_and_creator(self, tmp_path, build_ok=True, run_ok=True):
        runner = FakeSubprocessRunner()
        creator, template_dir, output_dir = make_creator(tmp_path, runner)
        template_dir.mkdir(parents=True, exist_ok=True)
        call_count = [0]
        original_run = runner.run

        def patched_run(cmd, **kwargs):
            call_count[0] += 1
            # build call
            if "build" in cmd:
                if not build_ok:
                    raise subprocess.CalledProcessError(1, cmd, stderr="Build failed")
            # docker run call — create the RAR file
            elif "run" in cmd and "--rm" in cmd:
                if not run_ok:
                    raise subprocess.CalledProcessError(1, cmd, stderr="Run failed")
                (output_dir / "test_archive.rar").write_bytes(b"FAKE_RAR")
            return original_run(cmd, **kwargs)

        runner.run = patched_run
        # Queue results
        runner.add_run(returncode=0)  # build
        if build_ok:
            runner.add_run(returncode=0)  # docker run
        return creator, output_dir, runner, call_count

    def test_builds_docker_image(self, tmp_path):
        creator, _, runner, _ = self._make_runner_and_creator(tmp_path)
        creator.create_rar_docker()
        build_calls = [c for c in runner.calls if "build" in c]
        assert len(build_calls) >= 1

    def test_runs_docker_container(self, tmp_path):
        creator, _, runner, _ = self._make_runner_and_creator(tmp_path)
        creator.create_rar_docker()
        run_calls = [c for c in runner.calls if "--rm" in c]
        assert len(run_calls) >= 1

    def test_docker_run_uses_volume_mount(self, tmp_path):
        creator, _, runner, _ = self._make_runner_and_creator(tmp_path)
        creator.create_rar_docker()
        run_calls = [c for c in runner.calls if "--rm" in c]
        assert any("-v" in c for c in run_calls)

    def test_docker_run_removes_container(self, tmp_path):
        creator, _, runner, _ = self._make_runner_and_creator(tmp_path)
        creator.create_rar_docker()
        run_calls = [c for c in runner.calls if "--rm" in c]
        assert len(run_calls) >= 1

    def test_skips_existing_rar_archive(self, tmp_path):
        runner = FakeSubprocessRunner()
        creator, _, output_dir = make_creator(tmp_path, runner)
        (output_dir / "test_archive.rar").write_bytes(b"EXISTING")
        creator.create_rar_docker()
        assert runner.call_count == 0

    def test_handles_docker_build_failure_gracefully(self, tmp_path):
        creator, _, runner, _ = self._make_runner_and_creator(tmp_path, build_ok=False)
        creator.create_rar_docker()  # must not raise
        run_calls = [c for c in runner.calls if "--rm" in c]
        assert len(run_calls) == 0

    def test_handles_docker_run_failure_gracefully(self, tmp_path):
        creator, _, runner, _ = self._make_runner_and_creator(tmp_path, run_ok=False)
        creator.create_rar_docker()  # must not raise


# ---------------------------------------------------------------------------
# TestCreateAll — orchestration
# ---------------------------------------------------------------------------


class TestCreateAll:
    def test_rpa_only_does_not_call_subprocess(self, tmp_path):
        runner = FakeSubprocessRunner()
        creator, template_dir, output_dir = make_creator(tmp_path, runner)
        template_dir.mkdir(parents=True, exist_ok=True)
        (template_dir / "file.txt").write_text("x")
        creator.create_all(rpa_only=True)
        assert runner.call_count == 0

    def test_rpa_only_creates_rpa_file(self, tmp_path):
        runner = FakeSubprocessRunner()
        creator, template_dir, output_dir = make_creator(tmp_path, runner)
        template_dir.mkdir(parents=True, exist_ok=True)
        (template_dir / "file.txt").write_text("x")
        creator.create_all(rpa_only=True)
        assert (output_dir / "test_archive.rpa").exists()

    def test_creates_output_dir_if_missing(self, tmp_path):
        runner = FakeSubprocessRunner()
        template_dir = tmp_path / "template"
        output_dir = tmp_path / "does_not_exist_yet"
        template_dir.mkdir()
        (template_dir / "file.txt").write_text("x")
        creator = ArchiveCreator(runner, output_dir, template_dir, FAKE_7Z)
        creator.create_all(rpa_only=True)
        assert output_dir.is_dir()

    def test_create_all_full_calls_7zip_and_rar(self, tmp_path):
        from unittest.mock import patch

        runner = FakeSubprocessRunner()
        creator, template_dir, output_dir = make_creator(tmp_path, runner)
        template_dir.mkdir(parents=True, exist_ok=True)
        (template_dir / "file.txt").write_text("x")
        with patch.object(creator, "create_7zip", return_value=[]) as m7z, patch.object(
            creator, "create_rar_docker", return_value=None
        ) as mrar:
            results = creator.create_all(rpa_only=False)
        m7z.assert_called_once()
        mrar.assert_called_once()
        assert "rpa" in results


class TestToDockerPath:
    def test_windows_converts_backslashes(self):
        from pathlib import Path
        from unittest.mock import patch

        path = Path("C:\\Users\\user\\archives")
        with patch("sys.platform", "win32"):
            result = ArchiveCreator._to_docker_path(path)
        assert "\\" not in result

    def test_linux_returns_path_as_str(self):
        from pathlib import Path
        from unittest.mock import patch

        path = Path("/home/user/archives")
        with patch("sys.platform", "linux"):
            result = ArchiveCreator._to_docker_path(path)
        assert result == str(path)
