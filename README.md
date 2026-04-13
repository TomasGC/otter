# Otter 🦦

Android archive extractor with progress tracking - MVP supports ZIP extraction

## Features

- 📦 Extract ZIP archives (RPA, RAR, TAR.GZ support coming post-MVP)
- 📊 Real-time progress tracking
- 🎨 Material Design 3 UI
- 🔒 Path traversal protection
- 🚀 "Open with" integration

## Architecture

- **MVVM + Clean Architecture** (UI → Domain → Data)
- **Jetpack Compose** - Declarative UI
- **Hilt** - Dependency injection
- **Kotlin Coroutines** - Async operations
- **StateFlow** - Reactive state management

## Requirements

- Docker & Docker Compose
- PowerShell 7+ (for build scripts)

## Build Commands

All builds run inside Docker (no local Android SDK needed):

```powershell
# Build debug APK
pwsh docker-build.ps1 assembleDebug

# Run unit tests
pwsh docker-build.ps1 testDebugUnitTest

# Run lint checks
pwsh docker-build.ps1 lintDebug

# Install on connected device
pwsh docker-build.ps1 installDebug

# List all tasks
pwsh docker-build.ps1 tasks
```

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

```powershell
# Build Docker image (one-time setup)
docker-compose build
```

### Run tests

```powershell
pwsh docker-build.ps1 testDebugUnitTest
```

### Build APK

```powershell
pwsh docker-build.ps1 assembleDebug
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
