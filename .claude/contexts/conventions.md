# Conventions - Otter

Coding and commit conventions for the Otter Android project.

---

## Commit Format

**Format**: `#XXX: type: description`

**Types**: feat, fix, refactor, test, docs, chore

**Examples**:
```
#25: feat: add archive browsing with sliding window cache
#25: fix: resolve content:// URI navigation for Samsung My Files
#25: test: add sliding window boundary tests
#25: chore: remove legacy repository and log files
```

**Rules**:
- Always prefix with GitHub issue number
- Description: WHAT/WHY, not HOW/WHO
- No stats (+XX lines), no implementation details, no emoji

---

## Branch Naming

- Features: `feature/#XXX-description`
- Bugfixes: `bugfix/#XXX-description`

---

## Kotlin/Android Conventions

### Package Structure

```
app.otter.ui.*        # UI Layer (Compose, ViewModels, State)
app.otter.domain.*    # Domain Layer (Models, Use Cases, Repositories)
app.otter.data.*      # Data Layer (Repository Impl, Extractors, Inspectors)
app.otter.di.*        # Dependency Injection modules
app.otter.service.*   # Android Services
app.otter.util.*      # Utilities
```

### Naming

- Activities: `*Activity.kt`
- ViewModels: `*ViewModel.kt`
- Composables: PascalCase functions
- Use Cases: `*UseCase.kt`
- Repositories: `*Repository.kt` (interface) + `*RepositoryImpl.kt` (impl)
- Inspectors: `*Inspector.kt`
- Browsers: `*Browser.kt`
- Sealed state classes: `*UiState.kt`

### Test Structure

```
src/test/java/app/otter/
├── unit/                  # Pure JVM unit tests
│   ├── data/              # Data layer tests
│   ├── domain/            # Domain layer tests
│   ├── service/           # Service tests
│   └── ui/viewmodel/      # ViewModel tests
├── integration/           # Integration tests (real files, mock Android)
│   ├── data/extractor/    # Extractor integration tests
│   ├── domain/usecase/    # Use case integration tests
│   ├── service/           # Service integration tests
│   └── viewmodel/         # ViewModel integration tests
└── integration-real/      # Tests against real archive files

src/androidTest/java/app/otter/
├── e2e/                   # End-to-end instrumented tests
├── helpers/               # Shared test helpers and base classes
└── domain/usecase/        # Instrumented use case tests
```

### Code Quality

1. **No hardcoded values** - Use constants or configuration
2. **One class/interface per file** - Single responsibility
3. **Strong typing** - Avoid `Any`, use sealed classes for state
4. **DRY** - No code duplication
5. **Immutability** - Prefer `val` over `var`, use data classes
6. **Null safety** - Leverage Kotlin's null-safety features
7. **Coroutines** - Use structured concurrency, inject `ioDispatcher`
8. **Clean Architecture** - Respect layer boundaries (UI → Domain → Data)

### Security

- Path traversal protection on all extracted paths
- ZIP bomb protection (100MB file limit)
- content:// URI preservation when ContentResolver cannot resolve

### Compose Instrumented Test Dispatcher

**Never call `Dispatchers.setMain(testDispatcher)` in tests that use `createComposeRule()`.**

Compose tests use `AndroidUiDispatcher.Main` internally — its frame clock drives `waitForIdle()`. Overriding `Dispatchers.Main` with `UnconfinedTestDispatcher` breaks this: state changes are delivered outside the clock's control, causing `ComposeNotIdleException` (30s timeout) on every interaction test.

```kotlin
// ❌ Bad — causes ComposeNotIdleException
@Before fun setup() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
}

// ✅ Good — inject testDispatcher only as ioDispatcher, leave Main alone
@Before fun setup() {
    val testDispatcher = UnconfinedTestDispatcher()
    viewModel = FileBrowserViewModel(ioDispatcher = testDispatcher, ...)
}
```

`viewModelScope.launch {}` (no dispatcher arg) uses real `Dispatchers.Main.immediate`, properly drained by `waitForIdle()`.
