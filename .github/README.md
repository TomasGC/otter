# GitHub Workflows

CI/CD pipelines for Otter Android app.

## Workflows

### CI (Continuous Integration)

**File:** `workflows/ci.yml`

**Triggers:**
- Push to `main` branch
- Pull requests targeting `main`

**Jobs:**
1. Build debug APK
2. Run unit tests
3. Run lint checks
4. Upload artifacts (APK, test results, lint reports)

**Usage:**
```bash
# Automatically runs on PR creation
# Check "Actions" tab on GitHub for results
```

### CD (Continuous Deployment)

**File:** `workflows/cd.yml`

**Triggers:**
- Push tags matching `v*` (e.g., `v1.0.0`)

**Jobs:**
1. Extract version from tag
2. Run release tests
3. Build release APK
4. Create GitHub Release with APK attachment
5. Upload APK artifact (90-day retention)

**Usage:**
```bash
# Update version in app/build.gradle.kts
# Commit and push
git add app/build.gradle.kts
git commit -m "chore: bump version to 1.0.0"
git push

# Create and push version tag
git tag v1.0.0
git push origin v1.0.0

# GitHub Actions will automatically:
# - Build release APK
# - Create GitHub Release
# - Attach APK to release
```

## Version Format

**SemVer:** `Major.Minor.Patch`

- **Major** (1.x.x) - Breaking changes, major refactor
- **Minor** (x.1.x) - New features (backward compatible)
- **Patch** (x.x.1) - Bug fixes only

**Example progression:**
- `1.0.0` (versionCode: 1) - MVP ZIP extraction
- `1.0.1` (versionCode: 2) - Fix path traversal bug
- `1.1.0` (versionCode: 3) - Add RPA format support
- `2.0.0` (versionCode: 4) - Complete UI redesign

**versionCode:** Increment manually in `app/build.gradle.kts` for each release.

## APK Signing

**Current:** Release APK is unsigned (for MVP).

**Future:** Add signing with GitHub Secrets:
- `KEYSTORE_FILE` - Base64-encoded keystore
- `KEYSTORE_PASSWORD` - Keystore password
- `KEY_ALIAS` - Key alias
- `KEY_PASSWORD` - Key password

## Artifacts

### CI Artifacts (7-day retention)
- `debug-apk` - Debug APK for testing
- `test-results` - JUnit test reports
- `lint-reports` - Lint HTML report

### CD Artifacts (90-day retention)
- `release-apk-{version}` - Release APK with version

## Monitoring

**Check pipeline status:**
- Repository → Actions tab
- Each workflow run shows detailed logs
- Failed runs send notifications (if configured)

**Download artifacts:**
- Actions → Select workflow run → Artifacts section
