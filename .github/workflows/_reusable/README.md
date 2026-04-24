# Reusable Workflows

Modularized GitHub Actions workflows for the Otter Android project.

**Uses native Gradle** (no Docker) for fast, simple CI/CD execution.

## Workflows

### Core Build & Test
- **build-apk.yml** - Builds debug or release APK with Gradle native
- **unit-tests.yml** - Runs unit tests with Gradle caching
- **instrumented-tests.yml** - Runs UI tests with Android emulator
- **instrumented-tests-matrix.yml** - Matrix testing across API levels (optional)

### Quality & Security
- **lint-checks.yml** - Android Lint, ktlint, detekt
- **coverage-merge.yml** - Merges unit + instrumented coverage, validates ≥75% threshold
- **security-checks.yml** - Dependency scan, secret scan, APK size check

## Why Native Gradle?

GitHub Actions runners **already have everything installed**:
- ✅ JDK 17
- ✅ Android SDK
- ✅ Gradle
- ✅ Build Tools

Docker adds:
- ❌ 3-5 min build time
- ❌ Complexity (artifact sharing issues)
- ❌ Extra layer

**Result**: Workflows are 3-5 min faster with native Gradle.

## Local Development

Use **Docker Compose** locally (avoids installing Android SDK on your PC):

```bash
# Local build with Docker
docker compose run --rm android-build ./gradlew assembleDebug

# CI uses native Gradle automatically
```

## Required Secrets

### Keystore Signing (CD only)
- `KEYSTORE_PASSWORD` - Release keystore password
- `KEY_PASSWORD` - Release key password

**Note**: Build.gradle.kts has fallback values for local testing.

## Configuration

### Coverage Threshold
Default: **75%** (configurable in ci.yml)

```yaml
coverage-merge:
  uses: ./.github/workflows/_reusable/coverage-merge.yml
  with:
    coverage-threshold: 80  # Override to 80%
```

### APK Size Limit
Default: **50MB** (hardcoded in security-checks.yml line 161)
