# Project Architecture - Otter (Android Archive Extractor)

**Purpose**: System architecture and design decisions for Otter (ZIP + RAR + 7z + TAR + RPA extraction with background service)
**Last Updated**: 2026-08-06

---

## Tech Stack

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
| **Build** | Gradle (KTS) + Python scripts (manage.py) | Kotlin DSL + cross-platform build/test/ADB automation via OOP DI scripts; pytest-xdist (-n auto) for unit + integ-mock Python suites |
| **Testing** | JUnit + MockK + Coroutines Test | See `contexts/tests.md` for current counts |
| **ZIP Extraction** | java.util.zip + IZipFileReader | Native ZIP (testable via interface) |
| **RAR Extraction** | 7-Zip-JBinding + MultiVolumeCallback | RAR4/RAR5 + split-archive (.part1.rar) support |
| **7z Extraction** | 7-Zip-JBinding + MultiVolumeCallback | 7-Zip format + split-archive (.7z.001) support |
| **TAR/GZIP Extraction** | Apache Commons Compress | TAR, TAR.GZ, TGZ, GZIP support |
| **RPA Extraction** | Custom (RpaPickleParser) | Ren'Py Archive (binary protocol 2) |
| **Archive Inspection** | ZipInspector, RpaInspector, TarInspector, GzipInspector, SevenZipBasedInspector | Lazy streaming entry enumeration for all supported formats |
| **Background Work** | Foreground Service + ExtractionQueue | Progress notifications, FIFO queue |

---

## System Architecture

### High-Level Architecture

```mermaid
graph TB
    subgraph "UI Layer"
        Screen[FileBrowserScreen<br/>Compose UI]
        Activity[ExtractionActivity<br/>Intent Handler]
        ViewModel[FileBrowserViewModel<br/>State Management]
        Service[ExtractionService<br/>Foreground Service]
    end
    
    subgraph "Domain Layer"
        BrowseUC[BrowseItemsUseCase<br/>paginated]
        ExtractUC[ExtractArchiveUseCase]
        RepoInterface[ArchiveRepository<br/>interface]
        BrowseRepoInterface[ItemBrowserRepository<br/>interface]
        Models[Domain Models<br/>BrowsableItem, ResourcePath<br/>ArchiveEntry, BrowseResult<br/>ExtractionResult]
    end
    
    subgraph "Data Layer"
        RepoImpl[ArchiveRepositoryImpl]
        BrowseRepoImpl[ItemBrowserRepositoryImpl<br/>polymorphic dispatch]
        BaseExtractor[BaseArchiveExtractor<br/>Template Method Pattern]
        
        subgraph "Inspectors"
            InspectorFactory[ArchiveInspectorFactory]
            ZipInsp[ZipInspector<br/>lazy streaming]
            RpaInsp[RpaInspector<br/>RpaPickleParser]
            TarInsp[TarInspector<br/>TAR/TAR_GZ/TAR_BZ2]
            GzipInsp[GzipInspector<br/>GZIP single-file]
            SevenZipInsp[SevenZipBasedInspector<br/>RAR/7z via 7-Zip-JBinding]
        end
        
        subgraph "Browsers"
            FSBrowser[FileSystemBrowser]
            ArchBrowser[ArchiveBrowser<br/>paginated]
        end
        
        subgraph "Extractors (Strategy Pattern)"
            ZipExt[ZipExtractor<br/>IZipFileReader]
            RarExt[RarExtractor<br/>7-Zip-JBinding]
            SevenZipExt[SevenZipExtractor<br/>7-Zip-JBinding]
            TarExt[TarExtractor<br/>Commons Compress]
            GzipExt[GzipExtractor<br/>Commons Compress]
            RpaExt[RpaExtractor<br/>Custom Binary]
        end
        
        LibMgr[ArchiveLibraryManager<br/>Singleton]
        PathVal[PathValidator<br/>Security]
        TempMgr[TempFileManager]
    end
    
    Activity --> Screen
    Screen --> ViewModel
    Service -.ExtractionEventBus.-> Screen
    ViewModel --> BrowseUC
    ViewModel --> ExtractUC
    Service --> ExtractUC
    BrowseUC --> BrowseRepoInterface
    ExtractUC --> RepoInterface
    BrowseRepoInterface -.implements.-> BrowseRepoImpl
    BrowseRepoImpl --> FSBrowser
    BrowseRepoImpl --> ArchBrowser
    ArchBrowser --> InspectorFactory
    InspectorFactory --> ZipInsp
    InspectorFactory --> RpaInsp
    InspectorFactory --> TarInsp
    InspectorFactory --> GzipInsp
    InspectorFactory --> SevenZipInsp
    RepoInterface -.implements.-> RepoImpl
    RepoImpl --> BaseExtractor
    BaseExtractor --> ZipExt
    BaseExtractor --> RarExt
    BaseExtractor --> SevenZipExt
    BaseExtractor --> TarExt
    BaseExtractor --> GzipExt
    BaseExtractor --> RpaExt
    RarExt --> LibMgr
    SevenZipExt --> LibMgr
    BaseExtractor --> PathVal
    BaseExtractor --> TempMgr
    BaseExtractor --> Logger
    
    style Screen fill:#e1f5ff
    style ViewModel fill:#fff4e1
    style Service fill:#ffe1f5
    style BrowseUC fill:#f0ffe1
    style ExtractUC fill:#f0ffe1
    style RepoImpl fill:#ffe1e1
    style BaseExtractor fill:#ffebcc
    style ZipExt fill:#ccf5ff
    style RarExt fill:#ccf5ff
    style SevenZipExt fill:#ccf5ff
    style TarExt fill:#ccf5ff
    style GzipExt fill:#ccf5ff
    style RpaExt fill:#ccf5ff
```

### Extraction Flow Sequence

```mermaid
sequenceDiagram
    participant User
    participant Activity as ExtractionActivity
    participant Service as ExtractionService
    participant UseCase as ExtractArchiveUseCase
    participant Repo as ArchiveRepositoryImpl
    participant Extractor as BaseArchiveExtractor
    participant FS as File System
    
    User->>Activity: Open archive file (Intent)
    Activity->>Service: Start foreground service
    Service->>UseCase: extract(uri, destination)
    UseCase->>Repo: extract(inputStream, type)
    Repo->>Repo: Select extractor by type
    Repo->>Extractor: extract(stream, dest, callback)
    
    loop For each file in archive
        Extractor->>Extractor: Validate path (security)
        Extractor->>FS: Write file
        Extractor->>Service: Progress update
        Service->>User: Notification progress
    end
    
    Extractor-->>Repo: ExtractionResult.Success
    Repo-->>UseCase: Result
    UseCase-->>Service: Result
    Service->>User: Completion notification
```

---

## Event-Driven Architecture

### ExtractionEventBus (StateFlow Pattern)

**Problem Solved**: SharedFlow replay mechanism caused timing issues where UI subscribers could miss events between flow creation and collection start.

**Solution**: Migrate to StateFlow for continuous state + SharedFlow for one-off completion events.

#### Architecture Comparison

| Aspect | SharedFlow (before) | StateFlow (after) |
|--------|-------------------|-------------------|
| **Initial value** | No value before first emit | Always has value (null or ProgressEvent) |
| **Late subscribers** | Replay buffer = 1 (can miss event if between emit/collect) | Get current state immediately |
| **Emission** | `suspend fun` required | Simple assignment `.value =` |
| **Use case** | One-off events | Continuous observable state |
| **Testing** | Requires `runTest` with timing management | Direct state checks, no timing issues |

#### Current Architecture

```kotlin
@Singleton
class ExtractionEventBus @Inject constructor() {
    data class ProgressEvent(
        val currentFile: String,
        val extractedCount: Int,
        val totalCount: Int,
        val progress: Float,
        val recentFiles: List<String>
    )
    
    private val _progressState = MutableStateFlow<ProgressEvent?>(null)
    val progressState: StateFlow<ProgressEvent?> = _progressState.asStateFlow()
    
    private val _completeEvents = MutableSharedFlow<Unit>(replay = 0)
    val completeEvents: SharedFlow<Unit> = _completeEvents.asSharedFlow()
    
    fun emitProgress(
        currentFile: String,
        extractedCount: Int,
        totalCount: Int,
        progress: Float,
        recentFiles: List<String>
    ) {
        _progressState.value = ProgressEvent(
            currentFile, extractedCount, totalCount, progress, recentFiles
        )
    }
    
    suspend fun emitComplete() {
        _completeEvents.emit(Unit)
    }
    
    fun reset() {
        _progressState.value = null
    }
}
```

#### Event Flow Sequence

```mermaid
sequenceDiagram
    participant Service as ExtractionService
    participant EventBus as ExtractionEventBus
    participant UI as ExtractionScreen
    
    Note over EventBus: _progressState = null (initial)
    
    Service->>EventBus: emitProgress(ProgressEvent)
    EventBus->>EventBus: _progressState.value = event
    
    UI->>EventBus: progressState.collect()
    EventBus-->>UI: event (current state)
    UI->>UI: Update UI
    
    Note over UI: Late subscriber scenario
    UI->>EventBus: progressState.collect()
    EventBus-->>UI: event (gets current state immediately!)
    
    Note over Service: Extraction complete
    Service->>EventBus: emitComplete()
    EventBus->>UI: completeEvents.emit()
    Service->>EventBus: reset()
    EventBus->>EventBus: _progressState.value = null
```

**Benefits**:
- ✅ No race conditions or timing issues
- ✅ Late subscribers always get current state
- ✅ Simpler testing (no `runTest` timing management)
- ✅ Clear separation: StateFlow for state, SharedFlow for events

---

### RecentFilesBuffer Component

**Purpose**: Circular FIFO buffer maintaining last N extracted files for UI display.

- **Fixed capacity**: 5 files maximum
- **FIFO**: Oldest file evicted when buffer full
- **Immutable snapshots**: `getFiles()` returns copy for UI thread safety

```kotlin
class RecentFilesBuffer(private val maxSize: Int = 5) {
    private val buffer = LinkedList<String>()
    
    fun add(fileName: String) {
        buffer.addLast(fileName)
        if (buffer.size > maxSize) {
            buffer.removeFirst()
        }
    }
    
    fun getFiles(): List<String> = buffer.toList()
    fun clear() = buffer.clear()
}
```

---

### UI Animation Patterns

- **Animatable**: Smooth progress interpolation (300ms tween) — eliminates frame-by-frame jumps
- **InfiniteTransition**: Animated "Starting..." dots (cycles 0→1→2→3 dots every 2s)

```kotlin
val animatedProgress = remember { Animatable(0f) }

LaunchedEffect(progress) {
    animatedProgress.animateTo(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300)
    )
}

LinearProgressIndicator(progress = animatedProgress.value)
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

## Archive Browsing (#25)

### Domain Model: Sealed Classes

```kotlin
sealed class ResourcePath {
    data class FileSystem(val path: String) : ResourcePath()
    data class ArchiveEntry(val archivePath: String, val entryPath: String) : ResourcePath()
    data class ContentUri(val uri: String) : ResourcePath()  // Samsung My Files URIs
}

sealed class BrowsableItem {
    data class FileSystemFile(val path: String, val name: String, val sizeBytes: Long, ...) : BrowsableItem()
    data class FileSystemDirectory(val path: String, val name: String) : BrowsableItem()
    data class ArchiveFile(val resourcePath: ResourcePath.ArchiveEntry, val name: String, ...) : BrowsableItem()
    data class ArchiveDirectory(val resourcePath: ResourcePath.ArchiveEntry, val name: String) : BrowsableItem()
}
```

### Archive Browsing Flow

```mermaid
sequenceDiagram
    participant User
    participant VM as FileBrowserViewModel
    participant UC as BrowseItemsUseCase
    participant Repo as ItemBrowserRepositoryImpl
    participant Browser as FileSystemBrowser / ArchiveBrowser
    participant Factory as ArchiveInspectorFactory
    participant Inspector as ZipInspector / RpaInspector

    User->>VM: browseDirectory(ResourcePath)
    VM->>UC: browse(path, page, pageSize)
    UC->>Repo: getItems(path, page, pageSize)
    
    alt ResourcePath.FileSystem
        Repo->>Browser: listDirectory(path, page)
        Browser-->>Repo: BrowseResult<BrowsableItem>
    else ResourcePath.ArchiveEntry
        Repo->>Browser: getEntries(archivePath, entryPath, page)
        Browser->>Factory: getInspector(archivePath)
        Factory->>Inspector: create (lazy, cached)
        Inspector-->>Browser: List<ArchiveEntry> (streaming)
        Browser-->>Repo: BrowseResult<BrowsableItem>
    end
    
    Repo-->>UC: BrowseResult
    UC-->>VM: BrowseResult
    VM->>VM: updateCache(page, items)
    VM-->>User: StateFlow<UiState.Success>
```

### Sliding Window Cache

Memory-efficient browsing for archives with 100k+ entries:

```
Constants (FileBrowserViewModel):
  PAGE_SIZE    = 200   entries loaded per page request
  HALF_WINDOW  = 100   pages retained on each side of viewport
  LOAD_TRIGGER = 60    pages from window edge → triggers background load

State:
  cachedPages: Map<Int, List<BrowsableItem>>  sparse page map
  minCachedPage / maxCachedPage               current window bounds
  totalItems: Int                             total entry count

Scroll detection (onScrollPositionChanged):
  absoluteIndex > maxCachedIndex - LOAD_TRIGGER  → loadNextPage()
  absoluteIndex < minCachedIndex + LOAD_TRIGGER  → loadPreviousPage()
  cleanup: evict pages outside [current - HALF_WINDOW, current + HALF_WINDOW]

Fast-scroll guard:
  lastKnownAbsoluteIndex prevents redundant consecutive loads
  hasMore=false short-circuits load at archive boundaries
```

### Samsung content:// URI Handling

Samsung My Files provides `content://` URIs that `ContentResolver.openFile()` cannot resolve to a file path:

```kotlin
fun fromUri(uri: Uri, contentResolver: ContentResolver): ResourcePath {
    val path = contentResolver.openFileDescriptor(uri, "r")?.use { ... }
    if (path != null) return ResourcePath.FileSystem(path)
    return ResourcePath.ContentUri(uri.toString())
}
```

### Selective Extraction

All extractors support `selectedItems: List<String>?`:
- `null` → extract all entries
- non-null → extract only paths matching the list

Propagation chain:
```
FileBrowserViewModel.selectedItems
  → ExtractionQueue.ExtractionTask(selectedItems)
    → ExtractionService.newIntent("extra_selected_items" extra)
      → ExtractArchiveUseCase(selectedItems)
        → ArchiveRepositoryImpl.extract(selectedItems)
          → ZipExtractor / RpaExtractor / ... .extract(selectedItems)
            → BaseArchiveExtractor.isEntrySelected(entryName, selectedPaths)
```

`isEntrySelected()` handles both exact file matches and directory prefix matches (`path.startsWith(dir + "/")`).

---

## Security Model

### Path Traversal Protection

```kotlin
private fun isValidPath(entryName: String): Boolean {
    val normalized = Paths.get(entryName).normalize().toString()
    return !normalized.startsWith("..") && !Paths.get(normalized).isAbsolute
}
```

### ZIP Bomb Protection

```kotlin
companion object {
    private const val MAX_FILE_SIZE = 100 * 1024 * 1024L // 100 MB
    private const val MAX_TOTAL_SIZE = 500 * 1024 * 1024L // 500 MB
}
```

### Permission Model

**Android 8-12** (API 26-32): `READ_EXTERNAL_STORAGE`

**Android 13+** (API 33+): No permission for Intent reads (scoped storage) + `POST_NOTIFICATIONS`

**Extraction Destination**: Always `Downloads/` folder — no `WRITE_EXTERNAL_STORAGE` needed (Android 10+)

---

## Performance Considerations

### ZIP Extraction (Issue #9)

**Problem**: Original approach took 15+ minutes for 2.6 GB archive (temp file + double-pass reading)

**Solution**: Direct stream + single-pass + 256 KB buffer (3-5x faster)

```kotlin
val buffer = ByteArray(256 * 1024)
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

**Gains**: eliminated temp I/O (~50%), 256 KB buffer (~15%), single-pass (~30%) → **3-5x faster**

### Progress Throttling

```kotlin
if (currentTime - lastNotificationTime > 1000) {
    lastNotificationTime = currentTime
    onProgress(ExtractionProgress.Extracting(...))
}
```

### Memory Management

- Process entries sequentially (not all in memory)
- 256 KB buffers, reused across files
- Direct stream extraction for ZIP (no temp files)
- Close streams in `try-finally`

---

## CI/CD Pipeline

**For detailed documentation, see [docs/CICD.md](../docs/CICD.md)**

GitHub Actions with language-prefixed workflows (GitHub does not support subdirectories under `.github/workflows/`):

**Key Workflows**:
- `push-ci.yml` → `kotlin.yml` + `python.yml` pipelines (feature/bugfix branch validation)
- `kotlin-*.yml` — Kotlin pipeline stages (validation, lint, unit-tests, integ-mock/real, build-apk, instrumented, coverage)
- `python-*.yml` — Python pipeline stages (detect-changes, lint, unit-tests, integ-mock/real, e2e, coverage)
- `pr-ci.yml` — Pull request validation
- `cd.yml` — Release pipeline

**CI Archive Generation** (archives/ is gitignored, generated on the fly):
1. `generate_archive_template.py` — creates `archives/template/` with valid test files
2. `create_test_archives.py --rpa-only` — creates `archives/test_archive.rpa` from template
3. Both require `PYTHONPATH=scripts/src` for cross-module imports

**Coverage**: ≥80% enforced via Kover
**Test Count**: 698 Kotlin + 628 Python script tests
**Success Rate**: 95-100% (Gradle Managed Devices)

---

## DI Modules

| Module | Scope | Purpose |
|--------|-------|---------|
| `AppModule` | `SingletonComponent` | Extractors, repositories, use cases, managers |
| `ViewModelModule` | `ViewModelComponent` | `startPath: ResourcePath?` — injectable initial navigation path (default `null` = file system root) |

`ViewModelModule` uses `@Provides fun provideStartPath(): ResourcePath? = null` so the start path can be overridden in tests or future deep-link flows without touching `FileBrowserViewModel`.

---

**End of Architecture Documentation**
