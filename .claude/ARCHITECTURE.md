# Project Architecture - Otter (Android Archive Extractor)

**Purpose**: System architecture and design decisions for Otter MVP (ZIP extraction)
**Last Updated**: 2026-04-13

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

## Module Structure (Otter MVP)

```
┌─────────────────────────────────────────────────────────────┐
│                     UI Layer (Compose)                       │
│  ┌───────────────┐  ┌────────────────┐  ┌────────────────┐ │
│  │ExtractionScreen│◄─┤ExtractionViewModel│◄─┤ExtractionUiState│ │
│  └───────────────┘  └────────────────┘  └────────────────┘ │
│         ▲                    │                                │
└─────────┼────────────────────┼────────────────────────────────┘
          │                    ▼
          │          ┌──────────────────────┐
          │          │  Domain Layer (Pure) │
          │          │  ┌──────────────────┐│
          │          │  │ExtractArchiveUseCase││
          │          │  └──────────────────┘│
          │          │         ▼            │
          │          │  ┌──────────────────┐│
          │          │  │ArchiveRepository │ (interface)
          │          │  └──────────────────┘│
          │          │         ▲            │
          │          │  ┌──────────────────┐│
          │          │  │  Domain Models   ││
          │          │  │ (ArchiveInfo,    ││
          │          │  │  ExtractionResult)││
          │          │  └──────────────────┘│
          │          └──────────────────────┘
          │                    │
          │                    ▼
          │          ┌──────────────────────────┐
          │          │   Data Layer (Android)    │
          │          │  ┌─────────────────────┐ │
          │          │  │ArchiveRepositoryImpl│ │
          │          │  └─────────────────────┘ │
          │          │           ▼               │
          │          │  ┌─────────────────────┐ │
          │          │  │   ZipExtractor      │ │
          │          │  │ (java.util.zip)     │ │
          │          │  └─────────────────────┘ │
          │          │           ▼               │
          │          │  ┌─────────────────────┐ │
          │          │  │  Android Storage    │ │
          │          │  │  (Downloads folder)  │ │
          │          │  └─────────────────────┘ │
          └──────────└──────────────────────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │  Hilt DI (AppModule)│
                    └─────────────────────┘
```

---

## Data Flow (Extraction Process)

```mermaid
sequenceDiagram
    participant User
    participant Activity as ExtractionActivity
    participant VM as ExtractionViewModel
    participant UC as ExtractArchiveUseCase
    participant Repo as ArchiveRepository
    participant Ext as ZipExtractor
    participant Storage as Android Storage

    User->>Activity: Select "Open with Otter"
    Activity->>Activity: Receive Intent (content URI)
    Activity->>VM: extractArchive(uri)
    
    VM->>VM: _uiState = Loading
    VM->>UC: invoke(uri, destination)
    
    UC->>Repo: extract(uri, destination)
    Repo->>Ext: extract(inputStream, outputDir)
    
    loop For each entry
        Ext->>Ext: Validate path (no traversal)
        Ext->>Ext: Validate size (no ZIP bomb)
        Ext->>Storage: Write file to Downloads
        Ext-->>Repo: Progress update
        Repo-->>UC: ExtractionProgress(X%)
        UC-->>VM: Flow<ExtractionProgress>
        VM-->>Activity: StateFlow update
        Activity->>User: Show progress (X%)
    end
    
    Ext-->>Repo: ExtractionResult.Success
    Repo-->>UC: Result
    UC-->>VM: Result
    VM->>VM: _uiState = Success
    Activity->>User: Show "Extraction complete!"
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
7. **Flow** - Reactive progress updates
8. **Unidirectional Data Flow** - UI → ViewModel → Use Case → Repository

---

## Performance Considerations

### Coroutines for Async Extraction

```kotlin
// Extraction runs on IO dispatcher (background thread)
suspend fun extract(uri: Uri, destination: File): Flow<ExtractionResult> = flow {
    withContext(Dispatchers.IO) {
        // ZIP extraction logic
    }
}.flowOn(Dispatchers.IO)
```

### Progress Updates

```kotlin
// Emit progress every N entries for smooth UI updates
private var entriesProcessed = 0
private const val PROGRESS_UPDATE_INTERVAL = 10

if (entriesProcessed % PROGRESS_UPDATE_INTERVAL == 0) {
    emit(ExtractionResult.Progress(...))
}
```

### Memory Management

- Process ZIP entries **sequentially** (not all in memory)
- Use `BufferedInputStream` / `BufferedOutputStream` for I/O efficiency
- Close streams in `try-finally` blocks

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

**End of Architecture Documentation**
