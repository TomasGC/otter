"""Shared ADB output fixtures for unit tests."""

ADB_DEVICES_EMPTY = "List of devices attached\n"
ADB_DEVICES_ONE = "List of devices attached\nABCD1234\tdevice\n"
ADB_DEVICES_MULTI = "List of devices attached\n" "ABCD1234\tdevice\n" "EF567890\tdevice\n"
ADB_DEVICES_MDNS = "List of devices attached\n" "adb-ABCD1234EFG-XyZ123 (2)._adb-tls-connect._tcp\tdevice\n"
ADB_DEVICES_CONNECTED = "List of devices attached\n192.168.1.1:5555\tdevice\n"
MDNS_ONE_DEVICE = "adb-SERIAL123-12345._adb-tls-connect._tcp\t" "_adb-tls-connect._tcp.\t192.168.1.1\t37905\n"
MDNS_TWO_DEVICES = (
    "adb-A1B2C3._adb-tls-connect._tcp\t_adb._tcp.\t192.168.1.1\t37905\n"
    "adb-D4E5F6._adb-tls-connect._tcp\t_adb._tcp.\t192.168.1.2\t37906\n"
)
ADB_DEVICES_EMULATOR = "List of devices attached\nemulator-5554\tdevice\n"
ADB_DEVICES_EMULATOR_OFFLINE = "List of devices attached\nemulator-5554\toffline\n"
AVD_LIST_ONE = "Pixel_6_API_34\n"
AVD_LIST_MULTI = "Pixel_6_API_34\nPixel_8_API_35\n"
