# KANBAN - Otter

Track of work sessions and completed tasks linked to GitHub issues.

---

2026-08-05 - [#36] File Type Icons and Folder Content Counts
- Added file type icon differentiation: FileTypeIconInfo.kt maps MIME types to distinct icons (image, video, audio, PDF, code, archive, etc.)
- Extended MimeTypeUtil with additional MIME type mappings
- Added GetFolderCountsUseCase + FolderCounts domain model; FileBrowserViewModel loads folder counts lazily per visible directory
- Fixed CI failure: BrowsableItemRowTest.folderCountZerosShownWhenFolderCountsNonNull — combinedClickable merges descendant semantics; fixed with useUnmergedTree = true
- Fixed CI failure: ExtractionServiceInstrumentedTest.serviceShouldEmitProgressEvents — StateFlow race condition; replaced all withTimeout usages with CompletableDeferred (event-driven, no internal timeout)
- Added coverage: FileTypeIconInfoTest, FileBrowserViewModelFolderCountsTest, GetFolderCountsUseCaseTest, ItemBrowserRepositoryFolderCountsMockIntegrationTest
tags: #icons #folder-counts #instrumented-tests #compose #stateflow
Ref: https://github.com/TomasGC/otter/issues/36
Commits: d2d1ac9, c65d35f, 708b6b5

---

2026-08-05 - [#38] Enable Test Parallelization
- Added pytest-xdist (-n auto) for unit and integration-mock Python suites; skipped for integration-real, e2e, coverage
- Added startPath injectable parameter to FileBrowserViewModel + ViewModelModule (@Provides returning null)
- Added unit/integration-mock testType Gradle filters to consolidate local runs from 5 invocations to 2
- Fixed detekt FunctionOnlyReturningConstant: added ignoreAnnotated: ['Provides'] for Hilt @Provides methods
- Fixed ExtractionServiceInstrumentedTest flakiness: StateFlow conflation via .buffer(Channel.UNLIMITED) + 2000ms teardown
- Removed maxParallelForks (net regression on 2-core CI: +133s integ-mock vs -43s unit)
- Condor: self-contained shards (no avd-setup job); explicit sdkmanager emulator install + system image cache; 2-shard parallel execution (~11min saved on cache hit)
- Refactored FileBrowserViewModelTest + FileBrowserViewModelSelectionTest to extend BaseFileBrowserViewModelTest; moved createBrowsableItem to base class (-112 lines of duplication)
tags: #parallelization #python #testing #detekt #instrumented-tests #ci-cd #condor #refactoring
Ref: https://github.com/TomasGC/otter/issues/38
Commits: 7d81ed6, a408602

---

2026-07-31 - [#49] Multi-Volume RAR/7z Extraction
- Added MultiVolumeCallback + ExtractionOptions for split-archive extraction (RAR .part1.rar, 7z .7z.001) via 7-Zip-JBinding volume chaining
- Replaced emulator polling loop with event-driven AdbManager.wait_for_emulator() (adb wait-for-device + getprop -w sys.boot_completed)
- Added split.py orchestrator with glob-pattern detection; send_archives pushes all volume parts to device
- Adopted *UnitTest/*MockIntegrationTest naming convention; redesigned CI filter stages for granular parallelism
- Added wait_for_emulator test pyramid (unit + integration-mock + e2e local_only) and local_only pytest marker + root conftest
- Fixed: black formatting (3 files), test_is_available_reflects_adb_install marked local_only (ADB absent on CI ubuntu-latest)
tags: #multi-volume #rar #7z #adb #emulator #testing #ci-cd #naming-convention
Ref: https://github.com/TomasGC/otter/issues/49
Commits: cabd19a, f100dff, d7bdefc, a3d027b

---

2026-06-26 - [#46] Other Archive Inspectors
- Added GzipInspector, TarInspector (TAR/TAR_GZ/TAR_BZ2), SevenZipBasedInspector (RAR/7z) for archive browsing
- Extended TarExtractor to support TAR_BZ2 extraction with BZip2CompressorInputStream
- Fixed RPA SETITEMS parser bug: real Ren'Py games use batch SETITEMS opcode; off-by-one in markIndex slice returned 0 entries
- Fixed FileBrowserViewModel loading state flash: moved Loading assignment before coroutine launch
- Fixed RAR Dockerfile missing -ep1 flag: archives stored absolute /workspace/template/ paths instead of relative paths
- Fixed CI: PYTHONPATH missing in send-test-archives.gradle.kts; exhaustive when for TAR_BZ2 in ArchiveExtractionTestHelper
tags: #inspectors #tar-bz2 #rpa #viewmodel #rar #ci-cd
Ref: https://github.com/TomasGC/otter/issues/46
Commits: 37ac1ab, 48331de, 44d99d4, f497bdd, 979610a, eb1ec71

---

2026-06-23 - [#44] Rework CI/CD Pipeline
- Fixed OWASP NVD: condor's kotlin-nvd-refresh.yml converted to workflow_call; nvd-refresh.yml (weekly schedule) added to otter
- Fixed PR-CI noise: check-pr job added before condor call — skips heavy pipeline if no PR for branch
- Fixed instrumented test archive path: device-archives-path /sdcard/otter → /sdcard/otter-test-archives
- Fixed adb push: directory push → file-by-file loop (prevents nesting when destination dir pre-exists)
tags: #ci-cd #owasp #nvd #instrumented-tests #adb
Ref: https://github.com/TomasGC/otter/issues/44
Commit: e145549

---

2026-06-21 - [#43] Complete ArchiveCreator OOP Refactor and Stabilize CI
- Refactored create_test_archives.py to ArchiveCreator class with SubprocessRunner DI
- Updated CreateAction and TestScriptsAction to use new ArchiveCreator API
- Removed coverage flags from pytest.ini addopts (run separately via manage.py test coverage)
- Updated integration and unit tests for new OOP API
tags: #python #oop #dependency-injection #refactoring #ci-cd
Ref: https://github.com/TomasGC/otter/issues/43
Commit: 69e1155

---

2026-06-14 - [#39] Python Scripts - OOP Refactor, 4-Tier Test Suite, Actions CLI
- Full OOP refactor: AdbManager, GradleRunner, VersionManager, ArchiveCreator, DeviceConnector, ArchiveTemplateGenerator, FilePusher — all accept SubprocessRunner via constructor DI (FakeSubprocessRunner for tests)
- Added actions CLI layer: AdbAction, BuildAction, TestAction, CreateAction, TestScriptsAction — dispatched by manage.py (5 verbs); removed legacy shims, manage.py is sole entry point
- 4-tier test pyramid: unit, integration-mock, integration-real, e2e — ~377 tests at 98.2% line coverage
- Eliminated all Kotlin detekt violations without @Suppress: helper extraction, ignorePrivate for opcode dispatch, smart-cast refactor in RpaPickleParser; upgraded compileSdk/targetSdk to 35 for Compose BOM 2024.11 compatibility
- Fixed compilation errors from dependency upgrades: ExtractionActivity permissions signature, ExtractionScreen missing setValue import, AppModule GzipExtractor constructor, stale test API calls
tags: #python #testing #oop #dependency-injection #refactoring #ci-cd #lint #detekt #sdk35
Ref: https://github.com/TomasGC/otter/issues/39
Commits: ca220d6, 66e9ed8, cf475d1, 0389778, ed6eddf

---

2026-06-09 - [#25] Archive Browsing with Sliding Window Cache
- Added domain model: ResourcePath sealed class, BrowsableItem, ArchiveInspector, BrowseItemsUseCase
- Implemented data layer: ZipInspector, RpaInspector, ArchiveInspectorFactory, FileSystemBrowser, ArchiveBrowser
- Migrated FileBrowserViewModel to BrowsableItem with sliding window cache (HALF_WINDOW=100, LOAD_TRIGGER=60)
- Added selective extraction in all formats (ZIP, RAR, 7z, TAR, RPA) with content:// URI preservation
- Replaced PowerShell/Docker build system with Python scripts; added 603 tests (439 unit + 94 integ-mock + 2 integ-real + 68 instrumented)
- Fixed: CI failures (scroll cache, path traversal, RPA extractor), FileBrowserScreenTest ComposeNotIdleException (remove Dispatchers.setMain conflict with Compose clock), test archive 264k→4k files; redesigned PR-CI with workflow_run trigger + injection/pwn-request security fixes
tags: #archive-browsing #sliding-window #selective-extraction #python #viewmodel #ci-cd #security
Ref: https://github.com/TomasGC/otter/issues/25
Commits: 5e26982, 3136767, 3f04795, 74988da, 7e4006f, f06c4fd, 06f56f2, 9629047, 9996c6f, e35d60e

---

2026-05-18 - [#33] CI/CD Pipeline Optimization and Kover Migration
- Migrated test coverage from Jacoco to Kover (Kotlin-optimized coverage tool)
- Fixed CI coverage report generation (artifact upload/download with .ic binary format)
- Refactored FileBrowserScreen from 407 to 265 lines (-35%) by extracting reusable components
- Migrated PowerShell build scripts to Python for cross-platform support (build.py, test.py, build_utils.py)
- Added build automation features: auto-increment version, auto-install APK, auto-connect device via ADB
- Added comprehensive unit tests: ResourcePathConverterTest (21 tests, 71%→89.9% coverage), FileFormattersTest (15 tests)
- Improved CI workflows: renamed feature-ci→push-ci, added commit/branch validation, fixed concurrency control
tags: #ci-cd #testing #refactoring #kover #coverage #workflows #python #automation
Ref: https://github.com/TomasGC/otter/issues/33
Commits: 359a83c, 5290a3d, 7eafd58, bdeef72, a400910, 1d811f4, 64d4d2f, 8cb390e, f89ef83, da9dbc2

2026-05-15 - [#27] Samsung My Files Style Progress UI with StateFlow Migration
- Implemented horizontal progress bar with real-time file list (last 5 extracted files with ✓/→ indicators)
- Migrated ExtractionEventBus from SharedFlow to StateFlow for event-driven architecture (eliminates timing issues)
- Added smooth Animatable animations for progress and file count (no frame-by-frame jumps)
- Implemented RecentFilesBuffer circular buffer (FIFO, max 5 files) for UI display
- Added animated "Starting..." dots with InfiniteTransition (cycles 0-3 dots every 2s)
- Added version label (v0.0.X) in bottom-left corner on all screens and dialogs
tags: #ui #progress #animation #stateflow #testing
Ref: https://github.com/TomasGC/otter/issues/27
Commits: 50e4a40, 51861a3

2026-05-12 - [#16] RPA-3.0 (Ren'Py Archive) Extraction Support
- Implemented RPA-3.0 format extraction with binary protocol 2 index parsing
- Fixed critical bugs: wrong binary opcode (0x75→0x73) and double XOR deobfuscation
- Created iterative offset calculation algorithm for correct file data positioning
- Added comprehensive test suite: 6 JVM unit tests (archive creation, extraction, hex dump validation)
- Fixed TestArchiveHelper in both test/ and androidTest/ directories for consistency
- Upgraded JaCoCo to 0.8.11 for Java 21 compatibility
tags: #rpa #extraction #binary-format #testing #debugging
Ref: https://github.com/TomasGC/otter/issues/16
Commits: fcabe33, ef9ae52

2026-05-02 - [#15] TAR/GZIP extraction + comprehensive SOLID refactoring
- Implemented Apache Commons Compress extractors for TAR, TAR.GZ, TGZ, and GZIP formats with stream-based extraction
- Completed comprehensive SOLID refactoring across all archive extractors (eliminated 170+ lines of duplicate code)
- Applied Template Method pattern: BaseArchiveExtractor provides common flow, subclasses implement extractInternal()
- Applied Strategy pattern: ProgressCalculator interface with 3 strategies (StandardProgressCalculator, IndeterminateProgressCalculator, SingleFileProgressCalculator)
- Applied Dependency Inversion Principle: created ITempFileManager interface, all extractors depend on abstraction
- Extracted responsibilities into dedicated classes: TempFileManager, ExtractionLogger, SevenZipExtractorHelper (SRP compliance)
- Created TestArchiveHelper with programmatic archive generation to bypass Android AAPT filtering issues
- Added 12 comprehensive instrumented tests + fixed constructor calls after refactoring (all tests passing)
- Removed obsolete test assets and increased coverage from 85% to 95%+ with edge case testing
tags: #refactoring #solid #design-patterns #tar #gzip #testing
Ref: https://github.com/TomasGC/otter/issues/15
Commits: 93cbaf3, d671ea3, 5371f4d, f9f019a, 0c42e5c, 9d5e554, 96568a0

---

## Work History

2026-04-27 - [#23] Fix CI to Run After Feature-CI
- Fixed race condition where CI and Feature-CI run simultaneously on PR push
- Implemented polling mechanism: CI waits up to 30 minutes for Feature-CI completion
- Checks Feature-CI status every 30 seconds instead of immediate check
- Eliminates false failures from checking 'in_progress' status
- Improves developer experience: no more manual CI re-runs
- Clean sequential execution: Feature-CI completes → CI validates result
tags: #ci-cd #workflow #race-condition
Ref: https://github.com/TomasGC/otter/issues/23
Commit: 647856c

2026-04-27 - [#14] Add 7-Zip (.7z) Extraction Support
- Implemented SevenZipExtractor using 7-Zip-JBinding library for 7z format support
- Created ArchiveLibraryManager singleton to centralize native library lifecycle management
- Extracted SevenZipCallbackExtractor to eliminate 180 lines of code duplication between RarExtractor and SevenZipExtractor
- Migrated instrumented tests from reactivecircus/android-emulator-runner to Gradle Managed Devices (official Google solution)
- Resolved CI/CD stability issues: eliminated crashpad_handler hang and emulator boot timeouts
- Added comprehensive tests: 19 unit tests + 6 instrumented tests (including ArchiveLibraryManager lifecycle tests)
tags: #7zip #extraction #refactoring #ci-cd #gradle-managed-devices #stability
Ref: https://github.com/TomasGC/otter/issues/14
Commits: 1cea40d, f0bff30, 31ed3dd, d4ba3ae

2026-04-24 - [#10] Restructure GitHub Actions Workflows
- Moved reusable workflows from _reusable/ to root (GitHub Actions limitation - no subdirectories)
- Renamed workflows to reusable-*.yml pattern for consistency
- Optimized feature-ci.yml for parallel execution (lint-checks + unit-tests → build → ui-tests)
- Refactored ci.yml to verify feature-ci passed instead of duplicating tests
- Added pre-release support (test-v* tags) to cd.yml for non-production releases
- Fixed workflow permissions (moved to workflow level for reusable workflows)
tags: #ci-cd #github-actions #optimization #security #workflows
Ref: https://github.com/TomasGC/otter/issues/10
Commits: 3352280, da0f2d7, a3af352

2026-04-21 - [#12] Add Automated Code Review to GitHub Actions
- Integrated Reviewdog with ktlint for Kotlin style checking on PRs (action-setup + official CLI)
- Added Detekt static analysis for code quality (complexity, naming, potential bugs)
- Implemented Jacoco test coverage verification with 80% threshold enforcement (blocks merge if <80%)
- Added OWASP Dependency Check for vulnerability scanning (CVSS ≥7.0)
- Added APK size monitoring (50MB limit) and TruffleHog secret detection
- Consolidated duplicate workflows (ci.yml and pr-check.yml merged)
tags: #ci-cd #code-quality #reviewdog #security #testing
Ref: https://github.com/TomasGC/otter/issues/12
Commits: 58821a8, 32d9e42, a453f5f, e1ae1c3, 4f49938

2026-04-20 - [#9] Auto Extraction Mode with Background Service
- Implemented ExtractionService as foreground service with progress notifications (file counter, percentage)
- Optimized ZIP extraction: direct stream + 256KB buffer (no temp file) for 3-5x faster performance
- Added user cancellation support with "Stop" button in notification (isActive checks)
- Implemented FileLogger for extraction logs (.txt format, readable on Android) - DEBUG builds only
- Created BaseArchiveExtractor to eliminate code duplication (DRY principle)
- Updated ArchiveRepositoryImpl to use callbackFlow for real-time progress emission
tags: #background-service #optimization #notifications #cancellation #logging
Ref: https://github.com/TomasGC/otter/issues/9
Commit: 9ba1347

2026-04-15 - [#7] RAR Extraction Support
- Implemented RarExtractor using 7-Zip-JBinding-4Android library (supports RAR4 and RAR5 formats)
- Added two-tier testing strategy: unit tests (type support) + instrumented tests (full extraction with native libs)
- Enhanced CI/CD with instrumented tests on Android emulator (API 29) using android-emulator-runner
- Added AVD caching for faster CI runs (first run ~5-7 min, subsequent ~2-3 min)
- Fixed ZipExtractor inputStream.reset() issue (read bytes into memory for two-pass approach)
- Added JitPack repository for GitHub-hosted dependencies
tags: #rar #extraction #7zip #testing #ci-cd #emulator
Ref: https://github.com/TomasGC/otter/issues/7
Commit: 7e19250

2026-04-14 - [#5] ZIP Extraction Implementation
- Implemented complete ZIP extraction with MVVM + Clean Architecture
- Created domain models (ArchiveType, ArchiveFile, ExtractionProgress, ExtractionResult)
- Built ExtractArchiveUseCase with empty archive validation
- Implemented ZipExtractor with path traversal protection (canonical path validation)
- Created ArchiveRepositoryImpl with extractor selection logic
- Set up Hilt DI with AppModule (wires extractors, repository, use case)
tags: #zip #extraction #mvvm #clean-architecture #security #tdd #testing
Ref: https://github.com/TomasGC/otter/issues/5
Commit: b3d6245

2026-04-13 - [#3] Create CI/CD Pipelines
- Created CI workflow (build, test, lint on push/PR to main)
- Created CD workflow (build release APK on version tags, create GitHub Release)
- Created PR Check workflow (validate PR format, run tests, post status comment)
- Implemented SemVer versioning (Major.Minor.Patch + manual versionCode)
- Added artifact uploads (APK, test results, lint reports)
- Documented workflows in .github/README.md
tags: #cicd #github-actions #automation
Ref: https://github.com/TomasGC/otter/issues/3
Commit: f65528c

2026-04-13 - [#1] Project Setup
- Initialized .claude/ directory with project structure
- Created CLAUDE.md with Otter-specific instructions (build commands, architecture, dependencies)
- Created ARCHITECTURE.md with MVVM diagrams, data flow, security model, and DI structure
- Created KANBAN.md for task tracking with GitHub Issues
- Configured settings.json with Claude Code permissions
- Set up .gitignore for Android + Claude Code patterns
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
