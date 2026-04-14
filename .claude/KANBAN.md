# KANBAN - Otter

Track of work sessions and completed tasks linked to GitHub issues.

---

## Work History

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
Commit: a67a9c9

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
Commit: 4d47d63

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
Commits: 9693b4f, f69dfa4

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
