# Tests - Otter

**Last Updated**: 2026-06-26

---

## App Tests (Android / Kotlin)

### Counts

| Category | Tests | Runner | Description |
|----------|-------|--------|-------------|
| Unit (JVM) | 495 | JUnit + MockK | Pure JVM, no Android deps |
| Integration mock (JVM) | 111 | JUnit + MockK + real files | Real archives, mocked Android |
| Integration real (JVM) | 2 | JUnit, no mocks | Real archives, no mocks at all |
| Instrumented (device) | 90 | AndroidJUnit4 + Hilt | Requires connected device |
| **Total** | **698** | | |

### Directory Structure

```
app/src/
├── sharedTest/java/app/otter/test/       # Shared between unit + instrumented
│   ├── ArchiveTestHelper.kt              # Programmatic ZIP creation (bypass AAPT)
│   ├── ExtractionTestHelper.kt           # Extraction assertion helpers
│   └── fakes/
│       ├── FakeZipFileReader.kt          # In-memory ZIP reader (no disk I/O)
│       ├── FakeZipFileReaderFactory.kt
│       └── SimpleTempFileManager.kt
│
├── test/java/app/otter/
│   ├── unit/                             # 439 tests — pure JVM, no Android deps
│   │   ├── data/
│   │   │   ├── browser/                  # ArchiveBrowser, FileSystemBrowser, pagination
│   │   │   ├── extractor/               # BaseArchiveExtractor, RPA creation/parsing/hex
│   │   │   ├── inspector/               # ZipInspector, ZipInspectorPath, RpaInspector, TarInspector, GzipInspector, SevenZipBasedInspector, ArchiveInspectorFactory
│   │   │   ├── repository/              # ArchiveBrowserRepositoryImpl, ArchiveRepositoryImpl, ItemBrowserRepositoryImpl
│   │   │   └── util/                    # ResourcePathConverter, RpaPickleParser
│   │   ├── domain/
│   │   │   ├── inspector/               # ArchiveEntry (domain inspector)
│   │   │   ├── model/                   # BrowsableItem, ResourcePath, ArchiveEntry, ArchiveType
│   │   │   └── usecase/                 # BrowseItemsUseCase, BrowseArchiveUseCase, ExtractArchiveUseCase, ExtractSelectedItemsUseCase
│   │   ├── service/                     # ExtractionQueue, ExtractionService, ExtractionEventBus, RecentFilesBuffer, NotificationHelper
│   │   ├── ui/viewmodel/               # FileBrowserViewModel:
│   │   │   │                            #   cache cleanup, cache expansion, concurrency,
│   │   │   │                            #   scroll detection, selection, base helpers,
│   │   │   │                            #   NaturalOrderComparator, ArchiveSelectionHelper
│   │   │   └── FileBrowserViewModelCacheTest.kt
│   │   └── util/                        # ArchiveFileFactory, ExtractionDestinationResolver,
│   │                                    # FileFormatters, FileLoggingTree, PathValidator
│   │
│   ├── integration/                     # 111 tests — real files + mocks, no Android runtime
│   │   ├── data/extractor/              # ZIP (mock ZipFileReader), RAR, TAR, 7z, RPA
│   │   │   ├── ZipExtractorIntegrationTest.kt     # 27 tests via FakeZipFileReader
│   │   │   ├── ZipExtractorIntegrationTestBase.kt # shared base
│   │   │   ├── RarExtractorTest.kt
│   │   │   ├── TarExtractorTest.kt
│   │   │   ├── SevenZipExtractorTest.kt
│   │   │   └── RpaExtractorTest.kt
│   │   ├── data/inspector/              # GzipInspectorIntegrationTest (8), TarInspectorIntegrationTest (9)
│   │   ├── domain/usecase/              # ExtractSelectedItemsIntegrationTest (10 tests)
│   │   ├── service/                     # ExtractionSelectedItemsIntegrationTest (5 tests)
│   │   │                                #   verifies selectedItems propagation via intent slot
│   │   └── viewmodel/                   # FileBrowserViewModel with real ZIP (300 entries)
│   │       ├── FileBrowserViewModelWindowIntegrationTest.kt  # sliding window scroll cycle
│   │       └── FileBrowserViewModelRealArchiveIntegrationTest.kt # filter+sort on real data
│   │
│   └── integration-real/               # 2 tests — real archives, zero mocks
│       └── data/extractor/
│           └── ZipExtractorRealIntegrationTest.kt  # 29 scenarios on actual ZIP files
│
└── androidTest/java/app/otter/          # 90 tests — instrumented, requires device
    ├── e2e/                             # End-to-end on real device
    │   ├── ExtractionActivityTest.kt    # Activity intent handling
    │   ├── ZipExtractorInstrumentedTest.kt
    │   ├── GzipInspectorInstrumentedTest.kt    # 7 tests
    │   ├── SevenZipBasedInspectorInstrumentedTest.kt  # 8 tests
    │   ├── TarInspectorInstrumentedTest.kt     # 7 tests
    │   └── domain/usecase/              # ZIP extract all, extraction, navigation workflows
    ├── helpers/                         # Shared infrastructure
    │   ├── HiltTestRunner.kt
    │   ├── BaseInstrumentedTest.kt
    │   ├── BaseArchiveExtractionTest.kt
    │   ├── BaseArchiveNavigationTest.kt
    │   ├── ArchiveExtractionTestHelper.kt
    │   ├── ArchiveNavigationTestHelper.kt
    │   ├── ArchiveSelectionTestHelper.kt
    │   ├── ComposeTestHelpers.kt
    │   ├── PermissionsHelper.kt
    │   └── TestArchiveHelper.kt         # Binary test archive in androidTest assets
    └── domain/usecase/helpers/
        └── TestConstants.kt             # Device paths (/storage/emulated/0/otter)
```

### Run Commands

```bash
# Unit + integration mock + integration real (fast, no device)
python scripts/manage.py test unit

# With Kover coverage report
python scripts/manage.py test coverage
# Report: app/build/reports/kover/html/index.html

# Instrumented tests (requires device connected via ADB)
python scripts/manage.py test instrumented

# Gradle direct (specific stage via -DtestType)
./gradlew testDebugUnitTest                                    # all JVM tests
./gradlew testDebugUnitTest -DtestType=unit-domain-service     # domain + service + util
./gradlew testDebugUnitTest -DtestType=unit-data               # data layer
./gradlew testDebugUnitTest -DtestType=unit-ui                 # UI + ViewModel
./gradlew testDebugUnitTest -DtestType=integration-mock-extractor
./gradlew testDebugUnitTest -DtestType=integration-mock-other
./gradlew testDebugUnitTest -DtestType=integration-real
./gradlew connectedDebugAndroidTest
./gradlew koverHtmlReportDebug
```

### CI Test Stages (parallel where possible)

```
lint (3 parallel)  ────────────────────────────────────────────────────┐
unit-domain-service ─┐                                                  │
unit-data           ─┼→ integ-mock-extractor ─┐                        │
unit-ui             ─┘  integ-mock-other     ─┼→ integ-real → build → instrumented
                                               └──────────→ coverage
```

**Parallelization constraint**: `maxParallelForks=1` (Robolectric compatibility). Parallelization only via separate CI jobs (each a separate JVM process).

### Test Archives

Tests that need real archive files use `System.getProperty("archives.dir")` (set to `<root>/archives/` by Gradle).

Generate locally:
```bash
# RPA only (pure Python, no Docker/7z)
python scripts/manage.py create archives --rpa-only

# All formats (requires 7-Zip + Docker for RAR)
python scripts/manage.py create archives
```

CI generates RPA automatically before running tests.

### Coverage Target

≥ 80% enforced in CI via Kover (replaced Jacoco in #33)

---

## Script Tests (Python)

### Counts

| Module | Tests | Description |
|--------|-------|-------------|
| `unit/android/test_adb_manager.py` | 20 | AdbManager class (DI pattern) |
| `unit/android/test_gradle_runner.py` | 10 | GradleRunner class (DI pattern) |
| `unit/android/test_version_manager.py` | 31 | VersionManager class (DI pattern) |
| `unit/cli/test_archive_creator.py` | 22 | ArchiveCreator class (ZIP/TAR/7z/RPA) |
| `unit/cli/test_archive_template_generator.py` | 30 | ArchiveTemplateGenerator (magic bytes, extensions, I/O) |
| `unit/cli/test_device_connector.py` | 58 | DeviceConnector class (mDNS, pairing, all branches) |
| `unit/cli/test_file_pusher.py` | 17 | FilePusher class (push, skip, mkdir, connector=None paths) |
| `unit/cli/test_manage.py` | ~15 | Manager dispatch + verb routing |
| `unit/cli/actions/test_adb_action.py` | ~13 | AdbAction (connect, send, lazy creation) |
| `unit/cli/actions/test_build_action.py` | ~15 | BuildAction (build, install, version, connector) |
| `unit/cli/actions/test_test_action.py` | ~25 | TestAction (unit, instrumented, coverage, device flow) |
| `unit/cli/actions/test_create_action.py` | ~10 | CreateAction (archives, template, rpa-only) |
| `unit/cli/actions/test_test_scripts_action.py` | ~10 | TestScriptsAction (all tiers) |
| `unit/common/test_console.py` | 14 | Console output formatting |
| `unit/common/test_file_utils.py` | 8 | File utility functions + load_test_settings |
| `integration_mock/cli/test_archive_creation.py` | 32 | ArchiveCreator integration (real FS, multiple formats) |
| `integration_mock/android/test_adb_integration.py` | 7 | ADB integration (real mDNS) |
| `integration_real/test_rpa_real.py` | 9 | RPA archive real creation + parsing |
| `integration_real/test_template_generator_real.py` | 8 | ArchiveTemplateGenerator real FS output |
| `integration_real/cli/test_archive_docker.py` | 6 | Archive creation via Docker (7z, tar) with real tools |
| **Total** | **~377** | 98.2% line coverage |

### Directory Structure

```
scripts/
├── manage.py                              # Entry point: python manage.py <verb> [args]
├── src/
│   ├── android/
│   │   ├── adb.py                        # AdbManager class
│   │   ├── gradle.py                     # GradleRunner class
│   │   └── versioning.py                 # VersionManager class
│   ├── cli/
│   │   ├── actions/                      # High-level CLI verb implementations
│   │   │   ├── adb.py                    # AdbAction (connect, send)
│   │   │   ├── build.py                  # BuildAction (build, install)
│   │   │   ├── create.py                 # CreateAction (archives, template)
│   │   │   ├── test.py                   # TestAction (unit, instrumented, coverage)
│   │   │   └── test_scripts.py           # TestScriptsAction (Python test tiers)
│   │   ├── adb_connect.py               # DeviceConnector class
│   │   ├── create_test_archives.py       # ArchiveCreator class
│   │   ├── generate_archive_template.py  # ArchiveTemplateGenerator class
│   │   ├── manage.py                     # argparse dispatcher → actions
│   │   └── send_to_phone.py             # FilePusher class
│   └── common/
│       ├── console.py                    # Rich console output
│       ├── constants.py                  # Shared string constants
│       ├── file_utils.py                 # Path helpers + load_test_settings
│       └── subprocess_runner.py          # SubprocessRunner protocol + RealSubprocessRunner
│
├── tests/
│   ├── helpers/
│   │   ├── fake_subprocess.py            # FakeSubprocessRunner + FakePopen (DI test doubles)
│   │   └── adb_fixtures.py              # Shared ADB test fixtures
│   ├── unit/
│   │   ├── android/                     # AdbManager, GradleRunner, VersionManager
│   │   ├── cli/
│   │   │   ├── actions/                 # Action class unit tests
│   │   │   ├── test_archive_creator.py
│   │   │   ├── test_archive_template_generator.py
│   │   │   ├── test_device_connector.py
│   │   │   ├── test_file_pusher.py
│   │   │   └── test_manage.py
│   │   └── common/                      # Console, file_utils
│   ├── integration_mock/                # Real FS + FakeSubprocessRunner
│   │   ├── cli/test_archive_creation.py
│   │   └── android/test_adb_integration.py
│   ├── integration_real/                # Real FS + real subprocess, no mocks
│   │   ├── cli/test_archive_docker.py   # Docker workspace under repo temp/ (pytest tmp_path ACLs block Docker mounts on Windows)
│   │   ├── test_rpa_real.py
│   │   └── test_template_generator_real.py
│   └── e2e/                             # End-to-end (real device/docker)
│
├── pytest.ini                            # Pytest config (markers: unit, integration)
└── requirements-test.txt
```

### Run Commands

```bash
# Via manage.py (recommended)
python scripts/manage.py test-scripts            # All tiers
python scripts/manage.py test-scripts unit       # Unit only (fast)
python scripts/manage.py test-scripts integration-mock
python scripts/manage.py test-scripts integration-real
python scripts/manage.py test-scripts coverage   # With coverage report

# Direct pytest (from repo root, with PYTHONPATH)
cd scripts && pytest
cd scripts && pytest --cov=src --cov-report=term-missing
cd scripts && pytest -m unit
```

### Coverage Target

≥ 80% (current: 98.2%)
