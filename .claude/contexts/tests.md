# Tests - Otter

**Last Updated**: 2026-06-02

---

## App Tests (Android / Kotlin)

### Counts

| Category | Tests | Runner | Description |
|----------|-------|--------|-------------|
| Unit (JVM) | 439 | JUnit + MockK | Pure JVM, no Android deps |
| Integration mock (JVM) | 94 | JUnit + MockK + real files | Real archives, mocked Android |
| Integration real (JVM) | 2 | JUnit, no mocks | Real archives, no mocks at all |
| Instrumented (device) | 68 | AndroidJUnit4 + Hilt | Requires connected device |
| **Total** | **603** | | |

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
│   │   │   ├── inspector/               # ZipInspector, ZipInspectorPath, RpaInspector, ArchiveInspectorFactory
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
│   ├── integration/                     # 94 tests — real files + mocks, no Android runtime
│   │   ├── data/extractor/              # ZIP (mock ZipFileReader), RAR, TAR, 7z, RPA
│   │   │   ├── ZipExtractorIntegrationTest.kt     # 27 tests via FakeZipFileReader
│   │   │   ├── ZipExtractorIntegrationTestBase.kt # shared base
│   │   │   ├── RarExtractorTest.kt
│   │   │   ├── TarExtractorTest.kt
│   │   │   ├── SevenZipExtractorTest.kt
│   │   │   └── RpaExtractorTest.kt
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
└── androidTest/java/app/otter/          # 68 tests — instrumented, requires device
    ├── e2e/                             # End-to-end on real device
    │   ├── ExtractionActivityTest.kt    # Activity intent handling
    │   ├── ZipExtractorInstrumentedTest.kt
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
python scripts/src/cli/test.py --unit

# With Kover coverage report
python scripts/src/cli/test.py --coverage
# Report: app/build/reports/kover/html/index.html

# Instrumented tests (requires device connected via ADB)
python scripts/src/cli/test.py --instrumented

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
python scripts/src/cli/create_test_archives.py --rpa-only

# All formats (requires 7-Zip + Docker for RAR)
python scripts/src/cli/create_test_archives.py
```

CI generates RPA automatically before running tests.

### Coverage Target

≥ 80% enforced in CI via Kover (replaced Jacoco in #33)

---

## Script Tests (Python)

### Counts

| Module | Tests | Description |
|--------|-------|-------------|
| `android/test_adb.py` | ~30 | ADB connect, device detection, mDNS pairing |
| `android/test_gradle.py` | ~25 | Gradle build, version increment, APK install |
| `android/test_versioning.py` | ~20 | Version code/name parsing and bumping |
| `common/test_console.py` | ~25 | Console output formatting, colors |
| `common/test_file_utils.py` | ~10 | File utility functions |

### Directory Structure

```
scripts/
├── src/                              # Source modules
│   ├── android/
│   │   ├── adb.py                   # ADB device management, mDNS auto-connect, pairing
│   │   ├── gradle.py                # Gradle task execution, APK install
│   │   └── versioning.py            # versionCode/versionName read + increment
│   ├── cli/
│   │   ├── build.py                 # Entry: build + install APK
│   │   ├── test.py                  # Entry: run unit/instrumented/coverage tests
│   │   ├── adb_connect.py           # Entry: auto-connect device via mDNS
│   │   ├── create_test_archives.py  # Create ZIP/RAR/7z/TAR/RPA test archives
│   │   ├── generate_archive_template.py  # Generate archive from template config
│   │   └── send_to_phone.py         # Push test archives to device via ADB
│   └── common/
│       ├── console.py               # Rich console output (colors, spinners, tables)
│       └── file_utils.py            # Path helpers
│
├── tests/
│   ├── unit/
│   │   ├── android/
│   │   │   ├── test_adb.py          # Unit tests for adb module
│   │   │   ├── test_gradle.py       # Unit tests for gradle module
│   │   │   └── test_versioning.py   # Unit tests for versioning module
│   │   └── common/
│   │       ├── test_console.py      # Unit tests for console module
│   │       └── test_file_utils.py   # Unit tests for file_utils module
│   └── integration/                 # (placeholder, future)
│
├── pytest.ini                        # Pytest config (markers: unit, integration)
├── requirements-test.txt             # pytest, pytest-cov, pytest-mock
└── docker/
    └── rar.Dockerfile               # Docker image for RAR archive creation (unrar-free)
```

### Run Commands

```bash
cd scripts

# All tests
pytest

# With coverage
pytest --cov=src --cov-report=term-missing

# Unit only (fast)
pytest -m unit

# Specific module
pytest tests/unit/android/test_adb.py -v

# Install test deps first
pip install -r requirements-test.txt
```

### Coverage Target

≥ 80%
