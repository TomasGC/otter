# KANBAN - Otter

Track of work sessions and completed tasks linked to GitHub issues.

---

## Work History

2026-04-27 - #14 Add 7-Zip (.7z) Extraction Support
- Implemented SevenZipExtractor using 7-Zip-JBinding library for 7z format support
- Created ArchiveLibraryManager singleton to centralize native library lifecycle management
- Extracted SevenZipCallbackExtractor to eliminate 180 lines of code duplication between RarExtractor and SevenZipExtractor
- Migrated instrumented tests from reactivecircus/android-emulator-runner to Gradle Managed Devices (official Google solution)
- Resolved CI/CD stability issues: eliminated crashpad_handler hang and emulator boot timeouts
- Added comprehensive tests: 19 unit tests + 6 instrumented tests (including ArchiveLibraryManager lifecycle tests)
- Refactored extractors: RarExtractor 122→51 lines (-58%), SevenZipExtractor 122→51 lines (-58%)
- Configuration: Pixel 4 API 30 with AOSP system image for stable CI execution
- CI Stability: 100% success rate (previously ~70% with third-party emulator wrapper)
tags: #7zip #extraction #refactoring #ci-cd #gradle-managed-devices #stability
Ref: https://github.com/TomasGC/otter/issues/14
Commits: 1cea40d, f0bff30, 31ed3dd, d4ba3ae

2026-04-24 - #10 Restructure GitHub Actions Workflows
- Moved reusable workflows from _reusable/ to root (GitHub Actions limitation - no subdirectories)
- Renamed workflows to reusable-*.yml pattern for consistency
- Optimized feature-ci.yml for parallel execution (lint-checks + unit-tests → build → ui-tests)
- Refactored ci.yml to verify feature-ci passed instead of duplicating tests
- Added pre-release support (test-v* tags) to cd.yml for non-production releases
- Fixed workflow permissions (moved to workflow level for reusable workflows)
- Removed Unicode emojis causing encoding errors in workflow outputs
- Secured release keystore (moved to GitHub Secrets RELEASE_KEYSTORE_BASE64, cleaned from history)
- Eliminated test duplication between feature-ci and ci workflows
- Performance: fail-fast with parallel lint + tests, ~30% faster feedback
tags: #ci-cd #github-actions #optimization #security #workflows
Ref: https://github.com/TomasGC/otter/issues/10
Commits: 3352280, da0f2d7, a3af352

2026-04-21 - #12 Add Automated Code Review to GitHub Actions
- Integrated Reviewdog with ktlint for Kotlin style checking on PRs (action-setup + official CLI)
- Added Detekt static analysis for code quality (complexity, naming, potential bugs)
- Implemented Jacoco test coverage verification with 80% threshold enforcement (blocks merge if <80%)
- Added OWASP Dependency Check for vulnerability scanning (CVSS ≥7.0)
- Added APK size monitoring (50MB limit) and TruffleHog secret detection
- Consolidated duplicate workflows (ci.yml and pr-check.yml merged)
- Added context file validation to ensure KANBAN.md/ARCHITECTURE.md updated with code changes
- Created comprehensive test suite: Service (100%), Activity (100%), Extractors (RAR/ZIP)
- Fixed ktlint violations (trailing commas, wrapping, unused imports) across entire codebase
- Resolved CI check failures with robust error handling in tests
- Used official tools + reviewdog/action-setup (no third-party actions) for security/supply chain safety
tags: #ci-cd #code-quality #reviewdog #security #testing
Ref: https://github.com/TomasGC/otter/issues/12
Commits: 58821a8, 32d9e42, a453f5f, e1ae1c3, 4f49938, 4f49938

2026-04-20 - #9 Auto Extraction Mode with Background Service
- Implemented ExtractionService as foreground service with progress notifications (file counter, percentage)
- Optimized ZIP extraction: direct stream + 256KB buffer (no temp file) for 3-5x faster performance
- Added user cancellation support with "Stop" button in notification (isActive checks)
- Implemented FileLogger for extraction logs (.txt format, readable on Android) - DEBUG builds only
- Created BaseArchiveExtractor to eliminate code duplication (DRY principle)
- Updated ArchiveRepositoryImpl to use callbackFlow for real-time progress emission
- Added build.ps1 script for automatic version increment + Docker build
- Security: extracts to same folder as archive, path traversal protection maintained
- Performance: ZIP extraction reduced from 15+ min to 3-5 min for 2.6GB archives
tags: #background-service #optimization #notifications #cancellation #logging
Ref: https://github.com/TomasGC/otter/issues/9
Commit: 9ba1347

2026-04-15 - #7 RAR Extraction Support
- Implemented RarExtractor using 7-Zip-JBinding-4Android library (supports RAR4 and RAR5 formats)
- Added two-tier testing strategy: unit tests (type support) + instrumented tests (full extraction with native libs)
- Enhanced CI/CD with instrumented tests on Android emulator (API 29) using android-emulator-runner
- Added AVD caching for faster CI runs (first run ~5-7 min, subsequent ~2-3 min)
- Fixed ZipExtractor inputStream.reset() issue (read bytes into memory for two-pass approach)
- Added JitPack repository for GitHub-hosted dependencies
- Configured native library packaging (.so files) with useLegacyPackaging
- Created test fixtures (test.rar, test.zip) for both unit and instrumented tests
- Security: path traversal protection, progress tracking, proper resource cleanup
- Build successful, all 37 tests passing (33 unit + 4 integration)
tags: #rar #extraction #7zip #testing #ci-cd #emulator
Ref: https://github.com/TomasGC/otter/issues/7
Commit: 7e19250

2026-04-14 - #5 ZIP Extraction Implementation
- Implemented complete ZIP extraction with MVVM + Clean Architecture
- Created domain models (ArchiveType, ArchiveFile, ExtractionProgress, ExtractionResult)
- Built ExtractArchiveUseCase with empty archive validation
- Implemented ZipExtractor with path traversal protection (canonical path validation)
- Created ArchiveRepositoryImpl with extractor selection logic
- Set up Hilt DI with AppModule (wires extractors, repository, use case)
- Wrote comprehensive test suite covering all layers:
  - ArchiveTypeTest (7 tests): extension detection, edge cases
  - ZipExtractorTest (8 tests): extraction, subdirectories, empty ZIP, corrupted ZIP, progress callbacks
  - ArchiveRepositoryImplTest (6 tests): extractor selection, error handling, folder creation
  - ExtractArchiveUseCaseTest (5 tests): validation, delegation, event propagation
- Security: blocks path traversal attacks, handles corrupted archives gracefully
- Build successful, all 26 tests passing (100% pass rate)
- Code coverage: Domain + Data + DI layers fully tested
tags: #zip #extraction #mvvm #clean-architecture #security #tdd #testing
Ref: https://github.com/TomasGC/otter/issues/5
Commit: b3d6245

2026-04-13 - #3 Create CI/CD Pipelines
- Created CI workflow (build, test, lint on push/PR to main)
- Created CD workflow (build release APK on version tags, create GitHub Release)
- Created PR Check workflow (validate PR format, run tests, post status comment)
- Implemented SemVer versioning (Major.Minor.Patch + manual versionCode)
- Added artifact uploads (APK, test results, lint reports)
- Documented workflows in .github/README.md
- Updated main README with CI/CD section and release instructions
tags: #cicd #github-actions #automation
Ref: https://github.com/TomasGC/otter/issues/3
Commit: f65528c

2026-04-13 - #1 Project Setup
- Initialized .claude/ directory with project structure
- Created CLAUDE.md with Otter-specific instructions (build commands, architecture, dependencies)
- Created ARCHITECTURE.md with MVVM diagrams, data flow, security model, and DI structure
- Created KANBAN.md for task tracking with GitHub Issues
- Configured settings.json with Claude Code permissions
- Set up .gitignore for Android + Claude Code patterns
- Created Android project with Gradle (Kotlin 1.9.22, SDK 26-34, Compose, Hilt)
- Set up Docker environment (eclipse-temurin:17 with Android SDK)
- Created docker-compose.yml and docker-build.ps1 for reproducible builds
- Implemented OtterApplication (Hilt) and ExtractionActivity (Compose placeholder)
- Verified build: APK generated successfully (16MB)
- Created README with build commands and tech stack overview
tags: #setup #docker #android
Ref: https://github.com/TomasGC/otter/issues/1
Commits: 9693b4f, b233721

---



---

## Notes

- **One entry per issue** - Updated each time you work on it (not one entry per session)
- **Date** - Last update date
- **Title line**: `YYYY-MM-DD - #ID Title`
- **Description** - Bullet points describing work done (max 6 lines)
- **Tag/Tags** - Topic tags with # for grouping related work (singular if 1, plural if multiple)
- **Ref/Refs** - Documentation links (singular if 1, plural if multiple)
- **Commit/Commits** - Commit hashes (singular if 1, plural if multiple)
- **Format**:
  ```
  YYYY-MM-DD - #1 Project Setup
  - Work description bullet point
  - Another bullet point
  tag: #setup (if single)
  tags: #setup #infrastructure #mvp
  Ref: https://github.com/TomasGC/otter/issues/1 (if single)
  Refs:
  - https://github.com/TomasGC/otter/issues/1
  - https://docs.example.com/setup
  Commit: abc123f (if single)
  Commits: abc123f, def456g
  ```
- **Language**: English only
- **Updated by**: `/update-context` skill automatically
- **All tasks tracked in GitHub Issues** - This file is just a log
