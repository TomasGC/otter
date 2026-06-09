#!/usr/bin/env python3
"""
Automatically find and connect to Android device using mDNS discovery.
Uses 'adb mdns services' to discover devices on local network.

First run: discovers devices, lets you choose if multiple, saves preference
Next runs: automatically connects to saved device
"""

import subprocess
import re
import sys
import json
from pathlib import Path
from typing import Optional


def get_config_file() -> Path:
    """Get path to config file storing saved device."""
    project_root = Path(__file__).parent.parent.parent.parent
    temp_dir = project_root / "temp"
    temp_dir.mkdir(exist_ok=True)
    return temp_dir / ".adb_device_cache.json"


def load_saved_device() -> Optional[str]:
    """Load saved device ID from config file."""
    config_file = get_config_file()
    if config_file.exists():
        try:
            with open(config_file, "r") as f:
                data = json.load(f)
                return data.get("device_id")
        except (FileNotFoundError, json.JSONDecodeError):
            # File doesn't exist or invalid JSON - expected scenarios
            return None
        except Exception as e:
            print(f"⚠️  Failed to load device config: {e}")
            return None
    return None


def save_device(device_id: str):
    """Save device ID to config file."""
    config_file = get_config_file()
    try:
        with open(config_file, "w") as f:
            json.dump({"device_id": device_id}, f)
    except Exception as e:
        print(f"⚠️  Failed to save device preference: {e}")


def clear_saved_device():
    """Clear saved device from config file."""
    config_file = get_config_file()
    if config_file.exists():
        try:
            config_file.unlink()
        except Exception as e:
            print(f"⚠️  Failed to clear device config: {e}")


def get_connected_devices() -> list[str]:
    """Get list of already connected ADB devices."""
    try:
        result = subprocess.run(["adb", "devices"], capture_output=True, text=True, timeout=3)
        connections = []
        for line in result.stdout.split("\n")[1:]:
            parts = line.strip().split()
            if len(parts) == 2 and parts[1] == "device":
                connections.append(parts[0])
        return connections
    except:
        return []


def discover_devices() -> list[tuple[str, str, str]]:
    """
    Discover ADB devices on network using mDNS.

    Returns:
        List of (device_id, ip, port) tuples
    """
    try:
        result = subprocess.run(
            ["adb", "mdns", "services"],
            capture_output=True,
            text=True,
            timeout=5,
        )

        devices = []
        for line in result.stdout.split("\n"):
            # Format: adb-ABCD1234EFG-XyZ123	_adb-tls-connect._tcp	192.168.1.146:39007
            match = re.match(r"^adb-([A-Z0-9]+)", line)
            if match:
                device_id = match.group(1)

                # Extract IP:port
                ip_port_match = re.search(r"(\d+\.\d+\.\d+\.\d+):(\d+)", line)
                if ip_port_match:
                    ip = ip_port_match.group(1)
                    port = ip_port_match.group(2)
                    devices.append((device_id, ip, port))

        return devices
    except Exception as e:
        print(f"⚠️  Failed to discover devices: {e}")
        return []


def is_device_connected(device_id: str) -> tuple[bool, Optional[str]]:
    """
    Check if device is already connected.

    Returns:
        (is_connected, connection_string) tuple
    """
    try:
        result = subprocess.run(
            ["adb", "devices"],
            capture_output=True,
            text=True,
            timeout=3,
        )

        for line in result.stdout.split("\n"):
            if device_id in line and "device" in line:
                # Extract connection string (IP:port or serial)
                connection = line.split()[0]
                return (True, connection)

        return (False, None)
    except:
        return (False, None)


def pair_device(ip: str, pairing_port: str, pairing_code: str) -> bool:
    """
    Pair with ADB device using pairing code.

    Args:
        ip: Device IP address
        pairing_port: Pairing port (shown on device, usually different from connect port)
        pairing_code: 6-digit pairing code shown on device

    Returns:
        True if pairing successful
    """
    try:
        result = subprocess.run(
            ["adb", "pair", f"{ip}:{pairing_port}"],
            input=pairing_code,
            capture_output=True,
            text=True,
            timeout=10,
        )

        return "successfully paired" in result.stdout.lower()
    except:
        return False


def connect_device(ip: str, port: str) -> bool:
    """Connect to ADB device at IP:port."""
    try:
        result = subprocess.run(
            ["adb", "connect", f"{ip}:{port}"],
            capture_output=True,
            text=True,
            timeout=5,
        )

        return (
            "connected" in result.stdout.lower()
            or "already connected" in result.stdout.lower()
        )
    except:
        return False


def choose_device(devices: list[tuple[str, str, str]]) -> Optional[tuple[str, str, str]]:
    """
    Let user choose device from multiple options.

    Args:
        devices: List of (device_id, ip, port) tuples

    Returns:
        Selected device tuple or None if cancelled
    """
    print("\n📱 Multiple devices found. Please choose:")
    for i, (device_id, ip, port) in enumerate(devices, 1):
        print(f"   {i}. {device_id} at {ip}:{port}")

    while True:
        try:
            choice = input("\nEnter device number (or 'q' to quit): ").strip()
            if choice.lower() == "q":
                return None

            index = int(choice) - 1
            if 0 <= index < len(devices):
                return devices[index]
            else:
                print("❌ Invalid choice. Try again.")
        except (ValueError, KeyboardInterrupt):
            print("\n❌ Cancelled")
            return None


def auto_connect(
    target_device: Optional[str] = None,
    pairing_code: Optional[str] = None,
    pairing_address: Optional[str] = None,
) -> Optional[str]:
    """
    Automatically discover and connect to Android device.

    Strategy:
    1. Check if device was saved previously → use it
    2. Discover devices via mDNS
    3. If 1 device found → connect automatically
    4. If multiple devices → ask user to choose once, save preference
    5. Next runs → use saved device

    Args:
        target_device: Optional device ID to search for (e.g., "ABCD1234EFG")
                      If None, uses auto-discovery

    Returns:
        Device connection string (IP:port) if successful, None otherwise
    """
    # Step 1: already connected?
    connected = get_connected_devices()
    if connected:
        print(f"✅ Already connected: {connected[0]}")
        return connected[0]

    # First-time pairing mode: user provides code and IP:PORT
    if pairing_code and pairing_address:
        print("🔐 First-time pairing mode...")
        print(f"   Pairing address: {pairing_address}")
        print(f"   Code: {pairing_code}")
        print(f"\n🔐 Pairing with {pairing_address}...")

        # Extract IP from address
        device_ip = pairing_address.split(":")[0]

        if pair_device(device_ip, pairing_address.split(":")[1], pairing_code):
            print(f"✅ Pairing successful!")
            print(f"\n🔍 Discovering device after pairing...")

            # After pairing, discover to get connect port
            devices = discover_devices()
            if not devices:
                print("❌ Device not discovered after pairing")
                print("   Try running script again to connect")
                return None

            # Find device by IP
            matching = [d for d in devices if d[1] == device_ip]
            if not matching:
                print(f"❌ Device at {device_ip} not found after pairing")
                return None

            device_id, ip, port = matching[0]
            print(f"   Found: {device_id} at {ip}:{port}")

            # Save for next time
            save_device(device_id)

            # Connect
            print(f"🔗 Connecting to {ip}:{port}...")
            if connect_device(ip, port):
                connection = f"{ip}:{port}"
                print(f"✅ Connected: {connection}")
                return connection
            else:
                print(f"❌ Connection failed after pairing")
                return None
        else:
            print(f"❌ Pairing failed. Check code and address.")
            return None

    # Normal discovery mode
    print("🔍 Discovering ADB devices via mDNS...")

    devices = discover_devices()

    if not devices:
        clear_saved_device()
        print("❌ No paired device found on network")
        print("   Pair first: python adb_connect.py --pair CODE --pair-address IP:PORT")
        return None

    # Deduplicate by device_id (keep first port found)
    seen_ids = set()
    unique_devices = []
    for device in devices:
        device_id = device[0]
        if device_id not in seen_ids:
            seen_ids.add(device_id)
            unique_devices.append(device)

    devices = unique_devices
    print(f"📱 Found {len(devices)} device(s)")

    # If target device specified (CLI argument), filter by it
    if target_device:
        devices = [d for d in devices if target_device in d[0]]
        if not devices:
            print(f"❌ Device '{target_device}' not found")
            return None

    # Strategy 1: Check saved device preference
    saved_device_id = load_saved_device()
    if saved_device_id:
        # Find saved device in discovered devices
        matching = [d for d in devices if d[0] == saved_device_id]
        if matching:
            device_id, ip, port = matching[0]
            print(f"   Using saved device: {device_id}")
        else:
            print(f"   Saved device '{saved_device_id}' not found on network (unpaired?)")
            # Clear stale cache
            clear_saved_device()
            # Fall through to auto-discovery
            saved_device_id = None

    # Strategy 2: Auto-discovery
    if not saved_device_id:
        if len(devices) == 1:
            # Only one device → connect automatically
            device_id, ip, port = devices[0]
            print(f"   Auto-selecting: {device_id}")
            save_device(device_id)  # Save for next time
        else:
            # Multiple devices → ask user to choose
            selected = choose_device(devices)
            if not selected:
                return None

            device_id, ip, port = selected
            print(f"   Selected: {device_id}")
            save_device(device_id)  # Save for next time

    # Check if already connected
    is_connected, existing_connection = is_device_connected(device_id)
    if is_connected:
        print(f"✅ Already connected: {existing_connection}")
        return existing_connection

    # Connect
    print(f"🔗 Connecting to {device_id} at {ip}:{port}...")
    if connect_device(ip, port):
        connection = f"{ip}:{port}"
        print(f"✅ Connected: {connection}")
        return connection
    else:
        print(f"❌ Failed to connect to {ip}:{port}")
        print("\n💡 First time? Need to pair with this device.")

        # Use CLI arguments if provided
        if pairing_code and pairing_port:
            print(f"\n🔐 Using provided pairing credentials...")
            print(f"   Code: {pairing_code}")
            print(f"   Port: {pairing_port}")
            print(f"🔐 Pairing with {ip}:{pairing_port}...")

            if pair_device(ip, pairing_port, pairing_code):
                print(f"✅ Pairing successful!")
                print(f"🔗 Now connecting to {ip}:{port}...")

                if connect_device(ip, port):
                    connection = f"{ip}:{port}"
                    print(f"✅ Connected: {connection}")
                    return connection
                else:
                    print(f"❌ Pairing worked but connection failed")
                    return None
            else:
                print(f"❌ Pairing failed. Check code and port.")
                return None

        # Interactive mode
        print(f"\n📱 On your device:")
        print(f"   1. Settings → Developer options → Wireless debugging")
        print(f"   2. Tap 'Pair device with pairing code'")
        print(f"   3. You'll see:")
        print(f"      - Wi-Fi pairing code: 123456 (6 digits)")
        print(f"      - IP address & Port: {ip}:XXXXX")
        print(f"")

        try:
            code_input = input("Enter the 6-digit pairing code: ").strip()
            address_input = input(f"Enter pairing address (IP:PORT, e.g., 192.168.1.146:33299): ").strip()

            # Parse IP:PORT
            if ":" not in address_input:
                print(f"❌ Invalid format. Expected IP:PORT (e.g., 192.168.1.146:33299)")
                return None

            pairing_ip = address_input.split(":")[0]
            pairing_port = address_input.split(":")[-1]

            print(f"\n🔐 Pairing with {address_input}...")
            if pair_device(pairing_ip, pairing_port, code_input):
                print(f"✅ Pairing successful!")
                print(f"\n🔍 Discovering device after pairing...")

                # After pairing, discover to get connect port
                devices = discover_devices()
                if not devices:
                    print("❌ Device not discovered after pairing")
                    print("   Try running script again")
                    return None

                # Find device by IP
                matching = [d for d in devices if d[1] == pairing_ip]
                if not matching:
                    print(f"❌ Device at {pairing_ip} not found after pairing")
                    return None

                device_id, discovered_ip, connect_port = matching[0]
                print(f"   Found: {device_id} at {discovered_ip}:{connect_port}")

                # Save device for next time
                save_device(device_id)

                print(f"🔗 Connecting to {discovered_ip}:{connect_port}...")
                if connect_device(discovered_ip, connect_port):
                    connection = f"{discovered_ip}:{connect_port}"
                    print(f"✅ Connected: {connection}")
                    return connection
                else:
                    print(f"❌ Connection failed after pairing")
                    return None
            else:
                print(f"❌ Pairing failed. Check code and address.")
                return None

        except (KeyboardInterrupt, EOFError):
            print("\n❌ Cancelled")
            return None


def main():
    """Main entry point."""
    import argparse
    import io

    # Fix Windows console encoding for emoji support
    if sys.platform == "win32":
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8")

    parser = argparse.ArgumentParser(
        description="Automatically discover and connect to Android device via mDNS"
    )
    parser.add_argument(
        "--device",
        help="Device ID to search for (e.g., ABCD1234EFG). If omitted, connects to first device found.",
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Suppress output (only return exit code)",
    )
    parser.add_argument(
        "--pair",
        metavar="CODE",
        help="6-digit pairing code (e.g., 922595)",
    )
    parser.add_argument(
        "--pair-address",
        metavar="IP:PORT",
        help="Pairing address from device (e.g., 192.168.1.146:43233)",
    )

    args = parser.parse_args()

    # Suppress output if quiet mode
    if args.quiet:
        sys.stdout = open("/dev/null" if sys.platform != "win32" else "nul", "w")

    connection = auto_connect(args.device, args.pair, args.pair_address)

    if connection:
        if not args.quiet:
            print(f"\n✅ Success! Device connected at {connection}")
        sys.exit(0)
    else:
        if not args.quiet:
            print("\n❌ Failed to connect to device")
        sys.exit(1)


if __name__ == "__main__":
    main()
