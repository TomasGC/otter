"""Manager — unified entry point for all Otter project operations."""

import sys
from pathlib import Path
from typing import Optional

sys.path.insert(0, str(Path(__file__).parent.parent))

from common.subprocess_runner import SubprocessRunner, RealSubprocessRunner
from cli.actions.build import BuildAction
from cli.actions.test import TestAction, SUITES as TEST_SUITES
from cli.actions.test_scripts import TestScriptsAction, SUITES as TEST_SCRIPTS_SUITES
from cli.actions.create import CreateAction, SUITES as CREATE_SUITES
from cli.actions.adb import AdbAction


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
            "suites", nargs="*",
            choices=TEST_SUITES,
            metavar="SUITE",
            help=f"Suites to run: {', '.join(TEST_SUITES)} (default: all; coverage overrides others)",
        )

        # test-scripts (Python pytest)
        tsp = sub.add_parser("test-scripts", help="Run Python script tests")
        tsp.add_argument(
            "suites", nargs="*",
            choices=TEST_SCRIPTS_SUITES,
            metavar="SUITE",
            help=f"Suites to run: {', '.join(TEST_SCRIPTS_SUITES)} (default: all; coverage overrides others)",
        )

        # create
        cp = sub.add_parser("create", help="Create test resources")
        cp.add_argument(
            "suites", nargs="*",
            choices=CREATE_SUITES,
            metavar="SUITE",
            help=f"Suites to run: {', '.join(CREATE_SUITES)} (default: all)",
        )
        cp.add_argument("--rpa-only", action="store_true")
        cp.add_argument("--output-dir", type=Path, default=None)

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
                rpa_only=args.rpa_only,
                output_dir=args.output_dir,
            )

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
