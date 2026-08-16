#!/usr/bin/env python3
"""ADB device auto-connect via mDNS discovery."""

import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Callable, Optional

from common.constants import (
    TIMEOUT_ADB_CONNECT,
    TIMEOUT_ADB_DEVICES,
    TIMEOUT_ADB_PAIR,
    TIMEOUT_MDNS,
)
from common.file_utils import get_project_root
from common.subprocess_runner import RealSubprocessRunner, SubprocessRunner


class DeviceConnector:
    """Discovers and connects to Android ADB devices via mDNS.

    Strategy (auto_connect):
    1. Explicit pairing credentials provided → pair and connect that device,
       regardless of what else is already connected
    2. Already connected → return immediately
    3. Discover via mDNS → single device auto-selects, multiple prompts user
    4. Saved preference reused on next run
    5. Interactive pairing if connection fails
    """

    def __init__(
        self,
        runner: SubprocessRunner,
        config_path: Path,
        input_fn: Callable[[str], str] = input,
    ) -> None:
        self._runner = runner
        self._config_path = config_path
        self._input = input_fn

    # -------------------------------------------------------------------------
    # Device config persistence
    # -------------------------------------------------------------------------

    def load_saved(self) -> Optional[str]:
        if not self._config_path.exists():
            return None
        try:
            data = json.loads(self._config_path.read_text())
            return data.get("device_id")
        except (json.JSONDecodeError, OSError):
            return None

    def save(self, device_id: str) -> None:
        try:
            self._config_path.parent.mkdir(parents=True, exist_ok=True)
            self._config_path.write_text(json.dumps({"device_id": device_id}))
        except OSError as e:
            print(f"Warning: failed to save device preference: {e}")

    def clear_saved(self) -> None:
        if self._config_path.exists():
            try:
                self._config_path.unlink()
            except OSError as e:
                print(f"Warning: failed to clear device config: {e}")

    # -------------------------------------------------------------------------
    # ADB queries
    # -------------------------------------------------------------------------

    def get_connected(self) -> list[str]:
        try:
            result = self._runner.run(
                ["adb", "devices"],
                capture_output=True,
                text=True,
                timeout=TIMEOUT_ADB_DEVICES,
            )
            connections = []
            for line in result.stdout.split("\n")[1:]:
                parts = line.strip().split()
                if len(parts) == 2 and parts[1] == "device":
                    connections.append(parts[0])
            return connections
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, FileNotFoundError):
            return []

    def discover(self) -> list[tuple[str, str, str]]:
        try:
            result = self._runner.run(
                ["adb", "mdns", "services"],
                capture_output=True,
                text=True,
                timeout=TIMEOUT_MDNS,
            )
            devices = []
            for line in result.stdout.split("\n"):
                match = re.match(r"^(adb-[A-Z0-9]+[^\\t]*)", line)
                if match:
                    ip_match = re.search(r"(\d+\.\d+\.\d+\.\d+):(\d+)", line)
                    if ip_match:
                        device_id = line.split("\t")[0].strip()
                        ip = ip_match.group(1)
                        port = ip_match.group(2)
                        devices.append((device_id, ip, port))
            return devices
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, FileNotFoundError) as e:
            print(f"Warning: mDNS discovery failed: {e}")
            return []

    def is_device_connected(self, device_id: str) -> tuple[bool, Optional[str]]:
        try:
            result = self._runner.run(
                ["adb", "devices"],
                capture_output=True,
                text=True,
                timeout=TIMEOUT_ADB_DEVICES,
            )
            for line in result.stdout.split("\n"):
                if device_id in line and "device" in line:
                    return (True, line.split()[0])
            return (False, None)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, FileNotFoundError):
            return (False, None)

    def pair(self, ip: str, port: str, code: str) -> bool:
        try:
            result = self._runner.run(
                ["adb", "pair", f"{ip}:{port}"],
                input=code,
                capture_output=True,
                text=True,
                timeout=TIMEOUT_ADB_PAIR,
            )
            return "successfully paired" in result.stdout.lower()
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, FileNotFoundError):
            return False

    def connect(self, ip: str, port: str) -> bool:
        try:
            result = self._runner.run(
                ["adb", "connect", f"{ip}:{port}"],
                capture_output=True,
                text=True,
                timeout=TIMEOUT_ADB_CONNECT,
            )
            return "connected" in result.stdout.lower() or "already connected" in result.stdout.lower()
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, FileNotFoundError):
            return False

    # -------------------------------------------------------------------------
    # Auto-connect orchestration
    # -------------------------------------------------------------------------

    def auto_connect(
        self,
        target_device: Optional[str] = None,
        pairing_code: Optional[str] = None,
        pairing_address: Optional[str] = None,
    ) -> Optional[str]:
        # Step 1: explicit pairing credentials always take priority — the caller is
        # asking to connect this specific device, regardless of what else (e.g. an
        # already-running emulator) happens to be connected right now.
        if pairing_code and pairing_address:
            return self._pair_and_connect(pairing_address, pairing_code)

        # Step 2: already connected?
        connected = self.get_connected()
        if connected:
            print(f"Already connected: {connected[0]}")
            return connected[0]

        # Step 3: discover via mDNS
        print("Discovering ADB devices via mDNS...")
        devices = self.discover()
        if not devices:
            self.clear_saved()
            print("No paired device found on network")
            return None

        # Deduplicate, keeping first occurrence of each device id
        unique: dict[str, tuple[str, str, str]] = {}
        for device in devices:
            unique.setdefault(device[0], device)
        devices = list(unique.values())
        print(f"Found {len(devices)} device(s)")

        if target_device:
            devices = [d for d in devices if target_device in d[0]]
            if not devices:
                print(f"Device '{target_device}' not found")
                return None

        # Use saved device preference if available
        saved_id = self.load_saved()
        if saved_id:
            matching = [d for d in devices if d[0] == saved_id]
            if matching:
                device_id, ip, port = matching[0]
                print(f"Using saved device: {device_id}")
            else:
                print(f"Saved device '{saved_id}' not found, re-discovering...")
                self.clear_saved()
                saved_id = None

        if not saved_id:
            if len(devices) == 1:
                device_id, ip, port = devices[0]
                print(f"Auto-selecting: {device_id}")
                self.save(device_id)
            else:
                chosen = self._choose_device(devices)
                if not chosen:
                    return None
                device_id, ip, port = chosen
                self.save(device_id)

        # Already connected?
        is_conn, existing = self.is_device_connected(device_id)
        if is_conn:
            print(f"Already connected: {existing}")
            return existing

        # Connect
        print(f"Connecting to {device_id} at {ip}:{port}...")
        if self.connect(ip, port):
            connection = f"{ip}:{port}"
            print(f"Connected: {connection}")
            return connection

        # Interactive pairing fallback
        return self._interactive_pair()

    # -------------------------------------------------------------------------
    # Private helpers
    # -------------------------------------------------------------------------

    def _pair_and_connect(self, pairing_address: str, pairing_code: str) -> Optional[str]:
        ip = pairing_address.split(":")[0]
        pair_port = pairing_address.split(":")[-1]
        if self.pair(ip, pair_port, pairing_code):
            print("Pairing successful")
            devices = self.discover()
            matching = [d for d in devices if d[1] == ip]
            if not matching:
                print("Device not found after pairing")
                return None
            device_id, d_ip, d_port = matching[0]
            self.save(device_id)
            if self.connect(d_ip, d_port):
                connection = f"{d_ip}:{d_port}"
                print(f"Connected: {connection}")
                return connection
        print("Pairing failed")
        return None

    def _interactive_pair(self) -> Optional[str]:
        print("\nOn your device: Settings -> Developer options -> Wireless debugging -> Pair device")
        try:
            code = self._input("Enter the 6-digit pairing code: ").strip()
            address = self._input("Enter pairing address (IP:PORT): ").strip()
            if ":" not in address:
                print("Invalid format. Expected IP:PORT")
                return None
            pair_ip, pair_port = address.split(":")
            if self.pair(pair_ip, pair_port, code):
                print("Pairing successful")
                devices = self.discover()
                matching = [d for d in devices if d[1] == pair_ip]
                if not matching:
                    return None
                _, d_ip, d_port = matching[0]
                self.save(matching[0][0])
                if self.connect(d_ip, d_port):
                    return f"{d_ip}:{d_port}"
            print("Pairing failed")
            return None
        except (KeyboardInterrupt, EOFError):
            print("\nCancelled")
            return None

    def _choose_device(self, devices: list[tuple[str, str, str]]) -> Optional[tuple[str, str, str]]:
        print("\nMultiple devices found. Choose:")
        for i, (did, ip, port) in enumerate(devices, 1):
            print(f"  {i}. {did} at {ip}:{port}")
        try:
            choice = self._input("Enter device number (or 'q' to quit): ").strip()
            if choice.lower() == "q":
                return None
            idx = int(choice) - 1
            if 0 <= idx < len(devices):
                return devices[idx]
            print("Invalid choice")
            return None
        except (ValueError, KeyboardInterrupt):
            return None


def main():  # pragma: no cover
    import argparse
    import io

    if sys.platform == "win32":
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8")

    parser = argparse.ArgumentParser(description="Connect to Android device via mDNS")
    parser.add_argument("--device", help="Device ID to search for")
    parser.add_argument("--quiet", action="store_true")
    parser.add_argument("--pair", metavar="CODE")
    parser.add_argument("--pair-address", metavar="IP:PORT")
    args = parser.parse_args()

    if args.quiet:
        sys.stdout = open("nul" if sys.platform == "win32" else "/dev/null", "w")

    config = get_project_root() / "temp" / ".adb_device_cache.json"
    connection = DeviceConnector(RealSubprocessRunner(), config).auto_connect(args.device, args.pair, args.pair_address)
    sys.exit(0 if connection else 1)


if __name__ == "__main__":  # pragma: no cover
    main()
