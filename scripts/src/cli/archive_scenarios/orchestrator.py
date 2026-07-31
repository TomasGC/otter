"""Orchestrates every fixture scenario (Perfect, Corrupted, Empty, Malicious, Stress)
into the final set of test archives that get pushed to the device.
"""

from pathlib import Path

from cli.archive_scenarios.corrupted import CorruptedArchives
from cli.archive_scenarios.empty import EmptyArchives
from cli.archive_scenarios.malicious import MaliciousArchives
from cli.archive_scenarios.perfect import PerfectArchives
from cli.archive_scenarios.split import SplitArchives
from cli.archive_scenarios.stress import (
    DeepNestedArchives,
    LargeArchives,
    LongFilenameArchives,
)
from common.subprocess_runner import SubprocessRunner

EMPTY_TEMPLATE_DIRNAME = "template_empty"
LARGE_TEMPLATE_DIRNAME = "template_large"
DEEP_NESTED_TEMPLATE_DIRNAME = "template_deep_nested"
LONG_FILENAME_TEMPLATE_DIRNAME = "template_long_filename"


def create_all_fixture_archives(runner: SubprocessRunner, output_dir: Path, template_dir: Path) -> None:
    """template_dir holds the real template tree used by Perfect (and, by extension,
    Corrupted, which mutates Perfect's output). Empty/Large/DeepNested/LongFilename each
    need their own differently-shaped template tree, built as siblings of template_dir.
    """
    template_root = template_dir.parent

    perfect_results = PerfectArchives(runner, output_dir, template_dir).create_all()
    CorruptedArchives(perfect_results, output_dir).create_all()
    EmptyArchives(runner, output_dir, template_root / EMPTY_TEMPLATE_DIRNAME).create_all()
    MaliciousArchives(output_dir).create_all()
    SplitArchives(runner, output_dir, template_dir).create_all()
    LargeArchives(runner, output_dir, template_root / LARGE_TEMPLATE_DIRNAME).create_all()
    DeepNestedArchives(runner, output_dir, template_root / DEEP_NESTED_TEMPLATE_DIRNAME).create_all()
    LongFilenameArchives(runner, output_dir, template_root / LONG_FILENAME_TEMPLATE_DIRNAME).create_all()
