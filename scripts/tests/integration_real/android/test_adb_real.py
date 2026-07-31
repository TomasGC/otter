#!/usr/bin/env python3
"""Integration-real tests for AdbManager — real adb subprocess, no mocks."""

import pytest

from android.adb import AdbManager
from common.subprocess_runner import RealSubprocessRunner

pytestmark = pytest.mark.integration_real


class TestAdbManagerReal:
    @pytest.mark.local_only
    def test_is_available_reflects_adb_install(self):
        # requires adb in PATH — not installed on CI ubuntu-latest runner
        assert AdbManager(RealSubprocessRunner()).is_available() is True

    def test_list_avds_returns_list_without_crash(self):
        result = AdbManager(RealSubprocessRunner()).list_avds()
        assert isinstance(result, list)
        for avd in result:
            assert isinstance(avd, str) and avd.strip()
