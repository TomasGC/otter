# Commands - Otter Build & Test

Build and test commands for the Otter Android project.

---

## Android Build & Test (Python Scripts)

```bash
# Build debug APK (auto-increments version, auto-installs on device)
python scripts/build.py

# Build without installing
python scripts/build.py --no-install

# Run unit tests only
python scripts/test.py --unit

# Run instrumented tests (requires connected device)
python scripts/test.py --instrumented

# Run all tests
python scripts/test.py

# Run unit tests with coverage report
python scripts/test.py --coverage
```

## Direct Gradle Commands (alternative)

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
./gradlew testDebugUnitTestCoverage
```

## Python Script Tests

```bash
cd scripts
pytest                                          # All tests
pytest --cov=src --cov-report=term-missing      # With coverage
pytest -m unit                                  # Unit only
pytest tests/unit/android/test_adb.py           # Specific file
```

## ADB Device Management

```bash
# Auto-connect via mDNS (built into build.py)
python scripts/src/cli/adb_connect.py

# Manual pairing (first time)
python scripts/build.py --pair
```

## Archive Test Assets

```bash
# Create test archives (requires Docker for RAR)
python scripts/src/cli/create_test_archives.py

# Send archives to device
python scripts/src/cli/send_to_phone.py
```
