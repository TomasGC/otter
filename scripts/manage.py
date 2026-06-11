#!/usr/bin/env python3
"""manage.py — Otter project manager.

Usage:
    python scripts/manage.py build [--no-install]
    python scripts/manage.py test [unit] [instrumented] [coverage]
    python scripts/manage.py test-scripts [unit] [integration-mock] [integration-real] [e2e] [coverage]
    python scripts/manage.py create [template] [archives] [--rpa-only] [--output-dir PATH]
    python scripts/manage.py adb connect [--device ID] [--pair CODE] [--pair-address IP:PORT]
    python scripts/manage.py adb send [files...] [--dest PATH] [--ci]
"""

import sys
from pathlib import Path

if __name__ == "__main__":  # pragma: no cover
    sys.path.insert(0, str(Path(__file__).parent / "src"))
    from cli.manage import Manager

    sys.exit(Manager().dispatch())
