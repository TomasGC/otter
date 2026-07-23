"""Stress archive fixtures — reuses Perfect's per-format classes against a specially
built template dir (many files / deep nesting / one very long filename).
"""

import random as _random
from pathlib import Path
from typing import Optional

from cli.archive_scenarios.base import ArchiveFormatScenario
from cli.archive_scenarios.perfect import default_formats
from cli.generate_archive_template import ArchiveTemplateGenerator
from common.subprocess_runner import SubprocessRunner


class LargeArchives(ArchiveFormatScenario):
    FILE_PREFIX = "large_test_archive"
    DEFAULT_FILE_COUNT = 10_000

    def __init__(
        self,
        runner: SubprocessRunner,
        output_dir: Path,
        template_dir: Path,
        file_count: int = DEFAULT_FILE_COUNT,
        rng: Optional[_random.Random] = None,
    ) -> None:
        ArchiveTemplateGenerator(rng).generate(template_dir, structure=[("bulk", file_count)], root_count=0)
        super().__init__(default_formats(runner, output_dir, template_dir, self.FILE_PREFIX))


class DeepNestedArchives(ArchiveFormatScenario):
    FILE_PREFIX = "deep_nested_test_archive"
    DEFAULT_DEPTH = 100

    def __init__(
        self,
        runner: SubprocessRunner,
        output_dir: Path,
        template_dir: Path,
        depth: int = DEFAULT_DEPTH,
        rng: Optional[_random.Random] = None,
    ) -> None:
        ArchiveTemplateGenerator(rng).generate_deep_nested(template_dir, depth=depth)
        super().__init__(default_formats(runner, output_dir, template_dir, self.FILE_PREFIX))


class LongFilenameArchives(ArchiveFormatScenario):
    FILE_PREFIX = "long_filename_test_archive"
    DEFAULT_MAX_LENGTH = 255

    def __init__(
        self,
        runner: SubprocessRunner,
        output_dir: Path,
        template_dir: Path,
        max_length: int = DEFAULT_MAX_LENGTH,
        rng: Optional[_random.Random] = None,
    ) -> None:
        ArchiveTemplateGenerator(rng).generate_long_filename(template_dir, max_length=max_length)
        super().__init__(default_formats(runner, output_dir, template_dir, self.FILE_PREFIX))
