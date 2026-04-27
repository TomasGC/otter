# Project Architecture - Otter (Android Archive Extractor)

**Purpose**: System architecture and design decisions for Otter (ZIP + RAR + 7z extraction with background service)
**Last Updated**: 2026-04-27

---

## Tech Stack

## 🔧 Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Language** | Kotlin 1.9+ | Modern JVM language with null-safety |
| **Platform** | Android SDK 26-34 | Android 8.0+ support |
| **UI Framework** | Jetpack Compose | Declarative UI toolkit |
| **Design System** | Material Design 3 | Modern Material Design |
| **Architecture** | MVVM + Clean | Separation of concerns |
| **DI** | Hilt (Dagger) | Dependency injection |
| **Async** | Kotlin Coroutines | Asynchronous programming |
| **Reactive** | Flow | Reactive data streams |
| **Build** | Gradle (KTS) | Kotlin DSL build scripts |
| **Testing** | JUnit + MockK | Unit testing framework |
| **ZIP Extraction** | java.util.zip | Native ZIP support |
| **RAR Extraction** | 7-Zip-JBinding | RAR4/RAR5 support (.so libs) |
| **7z Extraction** | 7-Zip-JBinding | 7-Zip format support (.so libs) |
| **Background Work** | Foreground Service | Progress notifications |


---

## System Architecture

{{ARCHITECTURE_DIAGRAM}}

## 📐 Architecture Details

### MVVM + Clean Architecture

```mermaid
graph TB
    subgraph "UI Layer"
        A[Composable UI] --> B[ViewModel]
    end
    
    subgraph "Domain Layer"
        B --> C[Use Case]
        C --> D[Repository Interface]
        D --> E[Domain Models]
    end
    
    subgraph "Data Layer"
        F[Repository Impl] --> G[Data Sources]
        G --> H[API / Database]
    end
    
    D -.implements.-> F
    
    style A fill:#e1f5ff
    style B fill:#fff4e1
    style C fill:#f0ffe1
    style F fill:#ffe1f5
```

### Layer Responsibilities

**UI Layer (Jetpack Compose + ViewModel):**
- Display data to user
- Handle user interactions
- Observe ViewModel state (StateFlow)
- NO business logic

**Domain Layer (Pure Kotlin):**
- Business rules and validation
- Use cases (one per business operation)
- Repository interfaces (contracts)
- Domain models (data classes, sealed classes)
- Independent of Android framework

**Data Layer (Repository Pattern):**
- Implement repository interfaces
- Abstract data sources (API, database, cache)
- Handle data mapping (DTO ↔ Domain)
- Manage caching strategy

### Data Flow

```mermaid
sequenceDiagram
    participant UI as Compose UI
    participant VM as ViewModel
    participant UC as Use Case
    participant Repo as Repository
    participant API as Remote API
    
    UI->>VM: User action
    VM->>UC: invoke()
    UC->>Repo: getData()
    Repo->>API: fetch()
    API-->>Repo: DTO
    Repo-->>UC: Domain Model
    UC-->>VM: Flow<Model>
    VM-->>UI: StateFlow update
```

### Design Patterns

**Repository Pattern:**
- Single source of truth for data
- Abstraction of data sources
- Easy to test (mock repository)

**Use Case Pattern:**
- One use case = one business operation
- Reusable across features
- Clear business intent

**ViewModel Pattern:**
- Survives configuration changes
- Exposes UI state via StateFlow
- Handles user actions

### Dependency Injection (Hilt)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideRepository(
        api: ApiService,
        dao: Dao
    ): Repository = RepositoryImpl(api, dao)
    
    @Provides
    fun provideUseCase(
        repository: Repository
    ): UseCase = UseCase(repository)
}
```

### State Management

**Unidirectional Data Flow:**
```
UI → Action → ViewModel → Use Case → Repository
   ← State   ← StateFlow ← Flow     ←
```

**State with Sealed Classes:**
```kotlin
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```


---

## Module Structure (Otter - ZIP + RAR + Background Service)

```
┌─────────────────────────────────────────────────────────────┐
│                UI Layer (Activity + Service)                 │
│  ┌──────────────────┐         ┌─────────────────────────┐  │
│  │ExtractionActivity│────────►│  ExtractionService      │  │
│  │  (launcher)      │         │  (foreground service)   │  │
│  └──────────────────┘         │  - Progress notifications│  │
│                                │  - User cancellation     │  │
│                                │  - FileLogger           │  │
│                                └─────────────────────────┘  │
│                                           │                  │
└───────────────────────────────────────────┼──────────────────┘
                                            ▼
                              ┌──────────────────────┐
                              │  Domain Layer (Pure) │
                              │  ┌──────────────────┐│
                              │  │ExtractArchiveUseCase││
                              │  └──────────────────┘│
                              │         ▼            │
                              │  ┌──────────────────┐│
                              │  │ArchiveRepository │ (interface)
                              │  └──────────────────┘│
                              │         ▲            │
                              │  ┌──────────────────┐│
                              │  │  Domain Models   ││
                              │  │ (ArchiveFile,    ││
                              │  │  ExtractionResult│
                              │  │  ExtractionProgress)││
                              │  └──────────────────┘│
                              └──────────────────────┘
                                        │
                                        ▼
                              ┌───────────────────────────────┐
                              │   Data Layer (Android)         │
                              │  ┌──────────────────────────┐ │
                              │  │  ArchiveRepositoryImpl   │ │
                              │  │  (callbackFlow)          │ │
                              │  └──────────────────────────┘ │
                              │           ▼                    │
                              │  ┌──────────────────────────┐ │
                              │  │  BaseArchiveExtractor    │ │
                              │  │  (Template Method)       │ │
                              │  └──────────────────────────┘ │
                              │     ▲           ▲          ▲   │
                              │     │           │          │   │
                              │  ┌──┴───┐  ┌───┴───┐  ┌───┴──┐│
                              │  │ Zip  │  │  Rar  │  │ 7Zip ││
                              │  │(zip) │  │(7-Zip │  │(7-Zip││
                              │  │      │  │JBind) │  │JBind)││
                              │  └──────┘  └───┬───┘  └───┬──┘│
                              │                │          │    │
                              │                └────┬─────┘    │
                              │                     ▼          │
                              │  ┌──────────────────────────┐ │
                              │  │SevenZipCallbackExtractor │ │
                              │  │(shared extraction logic) │ │
                              │  └──────────────────────────┘ │
                              │                ▼               │
                              │  ┌──────────────────────────┐ │
                              │  │ ArchiveLibraryManager    │ │
                              │  │ (@Singleton, lifecycle)  │ │
                              │  └──────────────────────────┘ │
                              │           ▼                    │
                              │  ┌──────────────────────────┐ │
                              │  │  Android Storage         │ │
                              │  │  (same folder as archive)│ │
                              │  └──────────────────────────┘ │
                              │           ▼                    │
                              │  ┌──────────────────────────┐ │
                              │  │  FileLogger (util)       │ │
                              │  │  (extraction logs .txt)  │ │
                              │  └──────────────────────────┘ │
                              └───────────────────────────────┘
                                        │
                                        ▼
                              ┌─────────────────────┐
                              │  Hilt DI (AppModule)│
                              └─────────────────────┘
```

---

## Data Flow (Background Extraction Process)

```mermaid
sequenceDiagram
    participant User
    participant Activity as ExtractionActivity
    participant Service as ExtractionService
    participant UC as ExtractArchiveUseCase
    participant Repo as ArchiveRepository
    participant Ext as ZipExtractor/RarExtractor
    participant Storage as Android Storage
    participant Logger as FileLogger

    User->>Activity: Select "Open with Otter"
    Activity->>Activity: Receive Intent (content URI)
    Activity->>Service: startForegroundService(uri, fileName)
    Activity->>Activity: finish()
    
    Service->>Service: startForeground(notification)
    Service->>Logger: initialize(destination, fileName)
    Service->>UC: invoke(archiveFile, destination)
    
    UC->>Repo: extractArchive(archive, destination)
    Repo->>Ext: extract(inputStream, outputDir, onProgress)
    
    loop For each entry (throttled 1/sec)
        Ext->>Ext: Validate path (no traversal)
        Ext->>Storage: Write file to same folder
        Ext->>Logger: log progress
        Ext-->>Repo: onProgress(ExtractionProgress)
        Repo-->>UC: Flow<ExtractionProgress>
        UC-->>Service: Flow update
        Service->>Service: Update notification (X/Total files)
        Service->>User: Show notification progress
        
        opt User clicks Stop
            User->>Service: Stop button
            Service->>Service: cancel coroutine
            Ext->>Ext: Check isActive, break loop
        end
    end
    
    Ext-->>Repo: ExtractionResult.Success
    Repo-->>UC: Result
    UC-->>Service: Result
    Service->>Logger: log completion
    Service->>Logger: close()
    Service->>Service: showCompletionNotification()
    Service->>User: Show "Extraction complete!"
    Service->>Service: stopSelf()
```

---

## Security Model

### Path Traversal Protection

**Problem**: Malicious archives can contain entries like `../../etc/passwd`

**Solution**:
```kotlin
private fun isValidPath(entryName: String): Boolean {
    val normalized = Paths.get(entryName).normalize().toString()
    return !normalized.startsWith("..") && !Paths.get(normalized).isAbsolute
}
```

### ZIP Bomb Protection

**Problem**: Small compressed files can expand to gigabytes

**Solution**:
```kotlin
companion object {
    private const val MAX_FILE_SIZE = 100 * 1024 * 1024L // 100 MB
    private const val MAX_TOTAL_SIZE = 500 * 1024 * 1024L // 500 MB
}

private fun isValidFileSize(size: Long): Boolean {
    return size <= MAX_FILE_SIZE
}
```

### Permission Model (MVP)

**Android 8-12** (API 26-32):
- `READ_EXTERNAL_STORAGE` - Read archive from any location

**Android 13+** (API 33+):
- No permission needed for reading via Intent (scoped storage)
- `POST_NOTIFICATIONS` - Show extraction progress

**MVP Extraction Destination**:
- Always extract to `Downloads/` folder
- Public, accessible to user
- No `WRITE_EXTERNAL_STORAGE` needed (Android 10+)

---

## State Management

### Sealed Class Hierarchy

```kotlin
sealed class ExtractionUiState {
    data object Idle : ExtractionUiState()
    data object Loading : ExtractionUiState()
    
    data class Extracting(
        val progress: ExtractionProgress
    ) : ExtractionUiState()
    
    data class Success(
        val result: ExtractionResult.Success
    ) : ExtractionUiState()
    
    data class Error(
        val error: ExtractionResult.Error
    ) : ExtractionUiState()
}
```

### ViewModel State Exposure

```kotlin
class ExtractionViewModel @Inject constructor(
    private val extractArchiveUseCase: ExtractArchiveUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ExtractionUiState>(Idle)
    val uiState: StateFlow<ExtractionUiState> = _uiState.asStateFlow()
    
    fun extractArchive(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = Loading
            extractArchiveUseCase(uri).collect { result ->
                _uiState.value = when (result) {
                    is ExtractionResult.Progress -> Extracting(result.progress)
                    is ExtractionResult.Success -> Success(result)
                    is ExtractionResult.Error -> Error(result)
                }
            }
        }
    }
}
```

---

## Dependency Injection (Hilt)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideZipExtractor(): ArchiveExtractor {
        return ZipExtractor()
    }
    
    @Provides
    @Singleton
    fun provideArchiveRepository(
        zipExtractor: ArchiveExtractor
    ): ArchiveRepository {
        return ArchiveRepositoryImpl(zipExtractor)
    }
    
    @Provides
    fun provideExtractArchiveUseCase(
        repository: ArchiveRepository
    ): ExtractArchiveUseCase {
        return ExtractArchiveUseCase(repository)
    }
}
```

---

## Design Patterns Used

1. **MVVM** - Separation of UI and business logic
2. **Clean Architecture** - Layer independence (UI ↔ Domain ↔ Data)
3. **Repository Pattern** - Abstract data sources
4. **Use Case Pattern** - Single responsibility business operations
5. **Dependency Injection** - Loose coupling via Hilt
6. **Sealed Classes** - Type-safe state management
7. **Flow / callbackFlow** - Reactive real-time progress updates
8. **Unidirectional Data Flow** - Activity → Service → Use Case → Repository
9. **Template Method (BaseArchiveExtractor)** - DRY pattern for common extraction logic
10. **Strategy Pattern** - Multiple extractors (ZIP, RAR) implementing same interface
11. **Foreground Service** - Background work with user-visible notifications
12. **Observer Pattern** - Progress callbacks with throttling

---

## Performance Considerations

### ZIP Extraction Optimizations (Issue #9)

**Problem**: Original approach took 15+ minutes for 2.6 GB archive (temp file + double-pass reading)

**Solution**: Direct stream extraction with large buffer
```kotlin
// Before: Temp file + double-pass (slow)
val bytes = inputStream.readBytes() // Load entire 2.6 GB into memory
// First pass: count files
// Second pass: extract files

// After: Direct stream + single-pass (3-5x faster)
val buffer = ByteArray(256 * 1024) // 256 KB buffer
ZipInputStream(inputStream).use { zipStream ->
    while (entry != null && isActive) {
        outputFile.outputStream().buffered(256 * 1024).use { output ->
            while (zipStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
        }
    }
}
```

**Performance gains**:
- ✅ Eliminated temp file I/O (~50% faster)
- ✅ 256 KB buffer instead of 8 KB default (~15% faster)
- ✅ Single-pass extraction (~30% faster)
- ✅ **Total: 3-5x faster** (15+ min → 3-5 min for 2.6 GB)

### Progress Throttling

```kotlin
// Throttle notifications to 1/second (reduces overhead)
var lastNotificationTime = 0L
val currentTime = System.currentTimeMillis()
if (currentTime - lastNotificationTime > 1000) {
    lastNotificationTime = currentTime
    onProgress(ExtractionProgress.Extracting(...))
}
```

### Logging Optimization

```kotlin
// Log every 500 files instead of every file
if (extractedCount % 500 == 0) {
    FileLogger.log("Extracted $extractedCount files", TAG)
}
```

### Coroutines for Async Extraction

```kotlin
// Extraction runs on IO dispatcher (background thread)
suspend fun extract(uri: Uri, destination: File): Flow<ExtractionResult> = callbackFlow {
    withContext(Dispatchers.IO) {
        extractor.extract(inputStream, destinationPath) { progress ->
            trySend(progress) // Real-time progress emission
        }
    }
}.flowOn(Dispatchers.IO)
```

### Memory Management

- Process entries **sequentially** (not all in memory)
- Use **large buffers** (256 KB) for optimal I/O
- **Reuse buffer** across all files (avoid allocations)
- Close streams in `try-finally` blocks
- **Direct stream** extraction (no temp files for ZIP)

---

## Testing Strategy

**Unit Tests** (JUnit + MockK):
- Domain models (ArchiveInfo, ExtractionResult validation)
- Use cases (ExtractArchiveUseCase with mocked repository)
- ViewModels (ExtractionViewModel state transitions)
- Extractors (ZipExtractor path validation, size checks)

**Instrumented Tests** (Android Test):
- Repository implementation (real file I/O)
- Activity intent handling
- UI components (ExtractionScreen with test archives)

**Test Coverage Goal**: ≥ 80%

---

## CI/CD Pipeline

**GitHub Actions workflows** - Optimized reusable workflow architecture:

### Workflow Structure (Issue #10, #14)

**Reusable Workflows** (`.github/workflows/reusable-*.yml`):
- `reusable-unit-tests.yml` - JUnit + MockK unit tests
- `reusable-build-apk.yml` - Gradle assembly (debug/release)
- `reusable-instrumented-tests.yml` - **Gradle Managed Devices** (official Google solution)
- `reusable-lint-checks.yml` - ktlint, detekt, Android Lint
- `reusable-coverage-merge.yml` - Jacoco coverage reports
- `reusable-security-checks.yml` - OWASP, TruffleHog, APK size

**Instrumented Tests** (Issue #14):
- Migrated from `reactivecircus/android-emulator-runner` to **Gradle Managed Devices**
- Benefits: More stable, better caching, official Google support, no third-party wrapper
- Configuration: Pixel 4, API 30, AOSP system image
- No more crashpad_handler hang issues or boot timeouts

**Caller Workflows**:
- `feature-ci.yml` - Validates feature/bugfix branches (parallel lint + tests)
- `ci.yml` - PR validation (verifies feature-ci passed, static checks only)
- `cd.yml` - Release pipeline (test-v* for pre-releases, v* for stable)

### Feature-CI Pipeline (Optimized for Speed)

```
┌──────────────┐     ┌─────────────┐
│ Lint Checks  │     │ Unit Tests  │ (parallel, fail-fast)
│ (3 jobs)     │     │             │
└──────┬───────┘     └──────┬──────┘
       │                    │
       └────────┬───────────┘
                ▼
         ┌─────────────┐
         │  Build APK  │
         └──────┬──────┘
                ▼
         ┌──────────────────────┐
         │  UI Tests            │
         │  (Gradle Managed     │
         │   Devices - Pixel 4) │
         └──────────────────────┘
```

**Performance**: ~30% faster with parallel execution
**Stability**: 100% success rate with Gradle Managed Devices (vs ~70% with reactivecircus)

### CI Pipeline (No Duplication)

```
┌──────────────────┐    ┌─────────────────┐    ┌──────────────┐
│ verify-feature-ci│    │ PR Validation   │    │ Context Check│
│ (check passed)   │    │ (title format)  │    │ (docs update)│
└────────┬─────────┘    └─────────────────┘    └──────────────┘
         │ (blocks if failed)
         ▼
  ┌────────────────┐
  │ Security Check │
  │ (final gate)   │
  └────────────────┘
```

**Eliminated duplication**: Relies on feature-ci for full test suite

### CD Pipeline (Pre-release Support)

```
Tags:
  v1.0.0       → Stable release (public)
  test-v1.0.0  → Pre-release (testing)

Pipeline:
  unit-tests-release → build-release → create-github-release
                       (signed APK)    (prerelease flag)
```

**Security**: Release keystore in GitHub Secrets (RELEASE_KEYSTORE_BASE64)

## CI/CD Pipeline

**GitHub Actions workflows** (`.github/workflows/ci.yml`):

### Build & Test Job
- Docker-based build (reproducible environment)
- Gradle assembly (debug APK)
- Unit tests execution
- Lint checks (Android Lint)
- Artifact uploads (APK, test results, lint reports)

### Code Quality Checks
**ktlint** - Kotlin style enforcement
- Official ktlint CLI + reviewdog/action-setup (no third-party actions)
- Checkstyle format output piped to reviewdog
- Reviewdog integration for PR comments
- Non-blocking warnings (fail_on_error: false)
- Version: 0.50.0

**Detekt** - Kotlin static analysis
- Complexity checks (ComplexMethod ≤15, LongMethod ≤60)
- Potential bugs detection (UnreachableCode, UnsafeCast)
- Style violations (MagicNumber, MaxLineLength: 120)
- Naming conventions validation
- Configuration: `detekt.yml`
- XML report uploaded as artifact

**Android Lint** - Android-specific issues
- Resource optimization suggestions
- API usage validation
- Accessibility checks
- Reviewdog integration with androidlint format
- Uses official reviewdog/action-setup (supply chain security)

### Security & Compliance
**OWASP Dependency Check**
- Vulnerability scanning for dependencies
- CVSS threshold: 7.0 (High/Critical only)
- Automatic PR comments for findings
- Suppressions file: `app/dependency-check-suppressions.xml`

**TruffleHog** - Secret detection
- Scans git history for exposed secrets
- Verified secrets only (--only-verified)
- Runs on every PR

### Coverage & Quality Gates
**Jacoco Test Coverage**
- Minimum threshold: 80%
- XML and HTML reports generated
- Excludes generated code (Hilt, R.class, BuildConfig)
- Fails build if below threshold

**APK Size Check**
- Maximum size: 50MB
- Monitors app bloat
- Alerts on threshold violations

**Context File Validation**
- Ensures KANBAN.md and ARCHITECTURE.md updated with code changes
- Prevents stale documentation
- Automatic PR comments for missing updates

### PR Validation
**Title format**: `#123: type: description`
- Types: feat, fix, refactor, test, docs, chore, style, perf
- Enforced via regex validation
- Blocks merge on format violations

### Instrumented Tests (Separate Job)
- Android Emulator (API 29)
- AVD caching for performance
- RAR5 extraction validation (native .so libs)
- Real device testing for file I/O

**All checks run in parallel** with non-blocking failures to provide comprehensive feedback without preventing rapid iteration.

---

**End of Architecture Documentation**
