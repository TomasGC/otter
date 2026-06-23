#!/usr/bin/env python3
"""Integration tests for RPA archive creation — pure Python, zero subprocess.

These tests use real filesystem I/O and real Python (no mocks at all).
They run in CI without any external tools.
"""

import pickle
import zlib
from pathlib import Path

import pytest

from cli.create_test_archives import RpaFormat

pytestmark = pytest.mark.integration_mock


def _make(tmp_path: Path, num_files: int = 5) -> tuple[RpaFormat, Path, Path]:
    tmpl = tmp_path / "template"
    out = tmp_path / "output"
    tmpl.mkdir()
    out.mkdir()
    for i in range(num_files):
        (tmpl / f"file_{i:03d}.txt").write_text(f"Content {i}\n", encoding="utf-8")
    sub = tmpl / "subdir"
    sub.mkdir()
    (sub / "nested.txt").write_text("Nested\n", encoding="utf-8")
    return RpaFormat(out, tmpl), tmpl, out


def _parse_index(rpa_path: Path) -> dict:
    data = rpa_path.read_bytes()
    parts = data[:34].decode("ascii").strip().split()
    idx_off = int(parts[1], 16)
    key = int(parts[2], 16)
    # Safe: archive was created by RpaFormat.create() above in same test session.
    raw = pickle.loads(zlib.decompress(data[idx_off:]))
    return {p: [[o ^ key, s ^ key] for o, s in entries] for p, entries in raw.items()}


class TestRpaRealCreation:
    def test_creates_valid_rpa(self, tmp_path):
        fmt, _, out = _make(tmp_path)
        fmt.create()
        assert (out / "test_archive.rpa").exists()

    def test_header_format(self, tmp_path):
        fmt, _, out = _make(tmp_path)
        fmt.create()
        header = (out / "test_archive.rpa").read_bytes()[:34].decode("ascii")
        parts = header.strip().split()
        assert parts[0] == "RPA-3.0"
        assert len(parts[1]) == 16
        assert len(parts[2]) == 8

    def test_all_files_in_index(self, tmp_path):
        fmt, tmpl, out = _make(tmp_path, num_files=3)
        fmt.create()
        index = _parse_index(out / "test_archive.rpa")
        assert "file_000.txt" in index
        assert "file_001.txt" in index
        assert "file_002.txt" in index
        assert "subdir/nested.txt" in index

    def test_file_offsets_past_header(self, tmp_path):
        fmt, _, out = _make(tmp_path)
        fmt.create()
        index = _parse_index(out / "test_archive.rpa")
        for path, entries in index.items():
            for offset, _ in entries:
                assert offset >= 34, f"{path}: offset {offset} is before header end"

    def test_stored_bytes_match_original(self, tmp_path):
        fmt, tmpl, out = _make(tmp_path, num_files=2)
        fmt.create()
        rpa_bytes = (out / "test_archive.rpa").read_bytes()
        index = _parse_index(out / "test_archive.rpa")
        for rpa_path, entries in index.items():
            offset, size = entries[0]
            end = offset + size
            stored = rpa_bytes[offset:end]
            fs_path = tmpl / rpa_path.replace("/", "\\")
            if not fs_path.exists():
                fs_path = tmpl / rpa_path
            assert stored == fs_path.read_bytes(), f"Content mismatch for {rpa_path}"

    def test_roundtrip_large_template(self, tmp_path):
        fmt, tmpl, out = _make(tmp_path, num_files=50)
        fmt.create()
        index = _parse_index(out / "test_archive.rpa")
        assert len(index) >= 51  # 50 flat + 1 nested

    def test_skip_does_not_modify_existing(self, tmp_path):
        fmt, tmpl, out = _make(tmp_path)
        sentinel = b"EXISTING_CONTENT_UNCHANGED"
        (out / "test_archive.rpa").write_bytes(sentinel)
        fmt.create()
        assert (out / "test_archive.rpa").read_bytes() == sentinel


class TestVersionManagerReal:
    """Version increment on a real gradle file copy — no mocks, real I/O."""

    GRADLE = """\
android {
    defaultConfig {
        versionCode = 10
        versionName = "0.0.10"
    }
}
"""

    def test_increments_code_in_real_file(self, tmp_path):
        from android.versioning import VersionManager

        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text(self.GRADLE, encoding="utf-8")
        code, name = VersionManager(tmp_path).increment(gradle)
        assert code == 11
        assert name == "0.0.11"

    def test_file_content_updated_after_increment(self, tmp_path):
        from android.versioning import VersionManager

        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text(self.GRADLE, encoding="utf-8")
        VersionManager(tmp_path).increment(gradle)
        content = gradle.read_text()
        assert "versionCode = 11" in content
        assert 'versionName = "0.0.11"' in content
