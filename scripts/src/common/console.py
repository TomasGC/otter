#!/usr/bin/env python3
"""Console utilities for logging and output formatting."""

import io
import sys
from datetime import datetime
from pathlib import Path

# Global log file handle
_log_file = None


def setup_windows_encoding():
    """Fix Windows console encoding for emoji support."""
    if sys.platform == "win32":
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8")


def setup_log_file(temp_dir: Path, prefix: str = "test") -> Path:
    """
    Setup log file for writing.

    Args:
        temp_dir: Directory to write log file
        prefix: Log file prefix (default: "test")

    Returns:
        Path to log file
    """
    global _log_file

    temp_dir.mkdir(exist_ok=True)
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    log_path = temp_dir / f"{prefix}-{timestamp}.log"

    _log_file = open(log_path, "w", encoding="utf-8", buffering=1)
    return log_path


def close_log_file():
    """Close log file if open."""
    global _log_file
    if _log_file:
        _log_file.close()
        _log_file = None


def log(message="", end="\n"):
    """Print to console and log file (if configured)."""
    print(message, end=end)
    if _log_file:
        _log_file.write(message + end)
        _log_file.flush()


def print_header(title: str):
    """Print formatted section header."""
    log("\n" + "=" * 60)
    log(f" {title}")
    log("=" * 60)
