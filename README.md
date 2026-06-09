# Otter 🦦

Android archive extractor with archive browsing and real-time progress tracking

## Features

- 📦 Extract ZIP, RAR, 7z, TAR, TAR.GZ, GZIP, RPA archives
- 🗂️ Browse archive contents before extracting (ZIP only)
- ✂️ Selective extraction — pick specific files/folders
- 📊 Real-time progress with file list (Samsung My Files style)
- 🎨 Material Design 3 UI
- 🔒 Path traversal and ZIP bomb protection
- 🚀 "Open with" integration (Samsung My Files, Files app)

## Architecture

- **MVVM + Clean Architecture** (UI → Domain → Data)
- **Jetpack Compose** - Declarative UI
- **Hilt** - Dependency injection
- **Kotlin Coroutines** - Async operations
- **StateFlow** - Reactive state management

## Requirements

- Python 3.8+
- Android SDK or connected Android device

## Build Commands (Kotlin/Android)

```bash
# Build debug APK (auto-increments version)
python scripts/build.py

# Build without installing
python scripts/build.py --no-install

# Run unit tests
python scripts/test.py --unit

# Run instrumented tests (requires device)
python scripts/test.py --instrumented

# Run all tests
python scripts/test.py

# Run tests with coverage
python scripts/test.py --coverage
```

## Test Python Scripts

The build/test scripts themselves have unit tests for reliability:

```bash
# Install test dependencies
pip install pytest pytest-cov pytest-mock

# Run Python script tests
cd scripts
pytest

# Run with coverage
pytest --cov=src --cov-report=term-missing

# Run only unit tests
pytest -m unit

# Run specific test file
pytest tests/unit/common/test_console.py
```

**Test structure:**
```
scripts/
├── src/                    # Source code
│   ├── common/            # Shared utilities
│   ├── android/           # Android build utilities
│   └── cli/               # CLI scripts
└── tests/                 # Test suite
    ├── unit/              # Unit tests
    ├── integration/       # Integration tests
    └── e2e/               # End-to-end tests
```

**Coverage target:** ≥80%

## Project Structure

```
app/src/main/java/app/otter/
├── ui/              # UI Layer (Compose, ViewModels, State)
├── domain/          # Domain Layer (Models, Use Cases, Repository interfaces)
├── data/            # Data Layer (Repository impl, Extractors)
└── di/              # Dependency Injection (Hilt modules)
```

## Development

### First-time setup

```bash
# Ensure Python dependencies (if any)
pip install -r requirements.txt  # If requirements.txt exists

# Connect Android device or start emulator
adb devices
```

### Run tests

```bash
# Unit tests only
python scripts/test.py --unit

# Instrumented tests (requires device)
python scripts/test.py --instrumented

# All tests with coverage
python scripts/test.py --coverage
```

### Build APK

```bash
# Build and auto-install on connected device
python scripts/build.py

# Build without installing
python scripts/build.py --no-install
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## CI/CD

### GitHub Actions Pipelines

**CI (Continuous Integration)**
- Triggers: Push to `main` + Pull Requests
- Jobs: Build, test, lint
- Artifacts: Debug APK, test results, lint reports

**CD (Continuous Deployment)**
- Triggers: Version tags (`v*`)
- Jobs: Build release APK, create GitHub Release
- Versioning: SemVer (Major.Minor.Patch)

**PR Check**
- Validates PR title format (#123: type: description)
- Runs full build + test + lint suite
- Posts results as PR comment

### Creating a Release

```bash
# Update version in app/build.gradle.kts
versionCode = 2
versionName = "1.0.1"

# Commit and push
git add app/build.gradle.kts
git commit -m "chore: bump version to 1.0.1"
git push

# Create and push tag
git tag v1.0.1
git push origin v1.0.1
```

GitHub Actions automatically builds and publishes the release.

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 1.9.22 |
| Platform | Android SDK | 26-34 |
| UI | Jetpack Compose | 1.6.1 |
| Design | Material Design 3 | 1.2.0 |
| DI | Hilt | 2.50 |
| Async | Coroutines | 1.7.3 |
| Build | Gradle | 8.2 |

## Security

- ✅ Path traversal protection
- ✅ ZIP bomb protection (100MB file limit)
- ✅ Storage permissions handled correctly
- ✅ No hardcoded secrets

## License

MIT
