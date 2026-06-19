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

## Build & Test

See [`.claude/contexts/commands.md`](.claude/contexts/commands.md) for the full command reference (build, test, ADB, archive creation, Python script tests).

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
# Connect Android device or start emulator
adb devices
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

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
