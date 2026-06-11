"""Create action — generate template files and test archives."""

from pathlib import Path
from typing import Optional

from common.subprocess_runner import SubprocessRunner

SUITES = ["template", "archives"]


class CreateAction:
    def __init__(
        self,
        runner: SubprocessRunner,
        output_dir: Optional[Path] = None,
        template_dir: Optional[Path] = None,
    ) -> None:
        self._runner = runner
        self._output_dir = output_dir
        self._template_dir = template_dir

    def run_template(self, output_dir: Optional[Path] = None) -> int:
        from cli.generate_archive_template import OUTPUT_DIR, ArchiveTemplateGenerator

        ArchiveTemplateGenerator().generate(output_dir or self._output_dir or OUTPUT_DIR)
        return 0

    def run_archives(self, rpa_only: bool = False, output_dir: Optional[Path] = None) -> int:
        from cli.create_test_archives import _DEFAULT_7Z, PROJECT_ROOT, ArchiveCreator
        from common.file_utils import load_test_settings

        out = output_dir or self._output_dir
        if out is None:
            out = PROJECT_ROOT / load_test_settings()["test_archives"]["host_path"]
        template = self._template_dir or (PROJECT_ROOT / "archives" / "template")
        ArchiveCreator(self._runner, out, template, _DEFAULT_7Z).create_all(rpa_only=rpa_only)
        return 0

    def run(
        self,
        suites: list[str] | None = None,
        rpa_only: bool = False,
        output_dir: Optional[Path] = None,
    ) -> int:
        suites = suites or []
        run_template = not suites or "template" in suites
        run_archives = not suites or "archives" in suites

        if run_template:
            rc = self.run_template(output_dir)
            if rc != 0:
                return rc

        if run_archives:
            rc = self.run_archives(rpa_only=rpa_only, output_dir=output_dir)
            if rc != 0:
                return rc

        return 0
