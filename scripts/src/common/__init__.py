"""Common utilities for all scripts."""

from .console import (
    close_log_file,
    log,
    print_header,
    setup_log_file,
    setup_windows_encoding,
)
from .file_utils import get_project_root

__all__ = [
    "setup_windows_encoding",
    "log",
    "print_header",
    "setup_log_file",
    "close_log_file",
    "get_project_root",
]
