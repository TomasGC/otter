# Commands - Otter Build & Test

Build and test commands for the Otter Android project.

---

## Android Build & Test (manage.py — recommended)

All Android operations go through `scripts/manage.py` from the repo root.

```bash
# Build debug APK (auto-increments version, auto-installs on device)
python scripts/manage.py build

# Build without installing on device
python scripts/manage.py build --no-install

# Run unit tests only (JVM, fast)
python scripts/manage.py test unit

# Run instrumented tests (requires connected device)
python scripts/manage.py test instrumented

# Run all tests (unit + instrumented)
python scripts/manage.py test

# Run unit tests with Kover coverage report
python scripts/manage.py test coverage
```

## Python Script Tests (manage.py)

```bash
# Run all Python script tests
python scripts/manage.py test-scripts

# Unit tests only (fast, no device)
python scripts/manage.py test-scripts unit

# Integration mock tests (real FS, fake subprocess)
python scripts/manage.py test-scripts integration-mock

# Integration real tests (real FS + real subprocess, no mocks)
python scripts/manage.py test-scripts integration-real

# E2E tests
python scripts/manage.py test-scripts e2e

# With coverage report
python scripts/manage.py test-scripts coverage
```

## ADB Device Management (manage.py)

```bash
# Auto-connect via mDNS
python scripts/manage.py adb connect

# Connect to specific device
python scripts/manage.py adb connect --device 192.168.1.1:5555

# Pair new device (first time, wireless debugging)
python scripts/manage.py adb connect --pair 123456 --pair-address 192.168.1.1:12345

# Send test archives to connected device
python scripts/manage.py adb send

# Send specific files to custom destination
python scripts/manage.py adb send file.zip --dest /sdcard/otter

# Send in CI (non-interactive)
python scripts/manage.py adb send --ci
```

## Archive Test Assets (manage.py)

```bash
# Create all test archives (requires 7-Zip + Docker for RAR)
python scripts/manage.py create archives

# Create RPA archive only (pure Python, no external deps)
python scripts/manage.py create archives --rpa-only

# Generate template files only
python scripts/manage.py create template

# Custom output directory
python scripts/manage.py create archives --output-dir /path/to/output
```

## Direct Gradle Commands (alternative)

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
./gradlew testDebugUnitTestCoverage
```

## Direct pytest (alternative, from scripts/)

```bash
cd scripts
pytest                                              # All tests
pytest --cov=src --cov-report=term-missing          # With coverage
pytest -m unit                                      # Unit only
pytest tests/unit/android/test_adb_manager.py -v   # Specific file
```
