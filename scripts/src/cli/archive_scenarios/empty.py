"""Empty archive fixtures — reuses Perfect's per-format classes against an empty template dir.

An "empty archive" is just an archive built from a directory with zero files, so no new
per-format logic is needed: default_formats() already handles every format correctly when
pointed at an empty template_dir (GzipSingleFileFormat returns None, since GZIP wraps
exactly one file's bytes and has no representation for "zero files" the way ZIP/TAR do).
"""

from pathlib import Path

from cli.archive_scenarios.base import ArchiveFormatScenario
from cli.archive_scenarios.perfect import default_formats
from common.subprocess_runner import SubprocessRunner


class EmptyArchives(ArchiveFormatScenario):
    FILE_PREFIX = "empty_test_archive"

    def __init__(self, runner: SubprocessRunner, output_dir: Path, empty_template_dir: Path) -> None:
        empty_template_dir.mkdir(parents=True, exist_ok=True)
        super().__init__(default_formats(runner, output_dir, empty_template_dir, self.FILE_PREFIX))
