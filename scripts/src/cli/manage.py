"""Manager — unified entry point for all Otter project operations."""

from pathlib import Path
from typing import Optional

from cli.actions.adb import AdbAction
from cli.actions.build import BuildAction
from cli.actions.coverage import CoverageAction
from cli.actions.create import SUITES as CREATE_SUITES
from cli.actions.create import CreateAction
from cli.actions.lint import LintAction
from cli.actions.test import SUITES as TEST_SUITES
from cli.actions.test import TestAction
from cli.actions.test_scripts import SUITES as TEST_SCRIPTS_SUITES
from cli.actions.test_scripts import TestScriptsAction
from cli.actions.validate import ValidateAction
from common.subprocess_runner import RealSubprocessRunner, SubprocessRunner


class Manager:
    """Dispatches project operations to action classes."""

    def __init__(self, runner: Optional[SubprocessRunner] = None) -> None:
        self._runner = runner or RealSubprocessRunner()

    def dispatch(self, argv: Optional[list] = None) -> int:
        import argparse

        p = argparse.ArgumentParser(prog="manage", description="Otter project manager")
        sub = p.add_subparsers(dest="command", required=True)

        # build
        bp = sub.add_parser("build", help="Build debug APK")
        bp.add_argument("--no-install", action="store_true")

        # test (Android)
        tp = sub.add_parser("test", help="Run Android tests")
        tp.add_argument(
            "suites",
            nargs="*",
            choices=TEST_SUITES,
            metavar="SUITE",
            help=f"Suites to run: {', '.join(TEST_SUITES)} (default: all)",
        )

        # test-scripts (Python pytest)
        tsp = sub.add_parser("test-scripts", help="Run Python script tests")
        tsp.add_argument(
            "suites",
            nargs="*",
            choices=TEST_SCRIPTS_SUITES,
            metavar="SUITE",
            help=f"Suites to run: {', '.join(TEST_SCRIPTS_SUITES)} (default: all; coverage overrides others)",
        )

        # create
        cp = sub.add_parser("create", help="Create test resources")
        cp.add_argument(
            "suites",
            nargs="*",
            choices=CREATE_SUITES,
            metavar="SUITE",
            help=f"Suites to run: {', '.join(CREATE_SUITES)} (default: all)",
        )
        cp.add_argument("--output-dir", type=Path, default=None)

        # validate
        sub.add_parser(
            "validate",
            help="Validate branch name, commit messages, no-TODO, large files",
        )

        # lint
        lp = sub.add_parser("lint", help="Run lint checks (kotlin, python, or deps)")
        lp.add_argument("target", choices=["kotlin", "python", "deps"])

        # coverage
        sub.add_parser("coverage", help="Generate Kover XML report and verify 80% threshold")

        # adb
        ap = sub.add_parser("adb", help="ADB device management")
        adb_sub = ap.add_subparsers(dest="subverb", required=True)

        adb_connect = adb_sub.add_parser("connect", help="Connect to device via mDNS")
        adb_connect.add_argument("--device", default=None)
        adb_connect.add_argument("--pair", metavar="CODE", default=None)
        adb_connect.add_argument("--pair-address", metavar="IP:PORT", default=None)

        adb_send = adb_sub.add_parser("send", help="Send test archives to device")
        adb_send.add_argument("files", nargs="*", type=Path)
        adb_send.add_argument("--dest", default=None)
        adb_send.add_argument("--ci", action="store_true")

        args = p.parse_args(argv)

        if args.command == "build":
            return BuildAction(self._runner).run(install=not args.no_install)

        if args.command == "test":
            return TestAction(self._runner).run(suites=args.suites)

        if args.command == "test-scripts":
            return TestScriptsAction(self._runner).run(suites=args.suites)

        if args.command == "create":
            return CreateAction(self._runner).run(
                suites=args.suites,
                output_dir=args.output_dir,
            )

        if args.command == "validate":
            return ValidateAction(self._runner).run()

        if args.command == "lint":
            return LintAction(self._runner).run(target=args.target)

        if args.command == "coverage":
            return CoverageAction(self._runner).run()

        if args.command == "adb":
            adb = AdbAction(self._runner)
            if args.subverb == "connect":
                return adb.run_connect(
                    device=args.device,
                    pair=args.pair,
                    pair_address=args.pair_address,
                )
            if args.subverb == "send":
                return adb.run_send(
                    files=args.files or None,
                    dest=args.dest,
                    ci=args.ci,
                )

        return 1  # pragma: no cover
