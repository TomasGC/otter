# Project Instructions - Otter

**Purpose**: Android Archive Extractor (MVP: ZIP) project instructions
**Last Updated**: 2026-04-13

---

## Hard Constraints (Non-Negotiable)

### Testing Requirements

**ALL TESTS MUST PASS** - No exceptions

After any code change:
1. Build: `pwsh.exe -File C:/dev/repos/GitHub/otter/build.ps1`
2. Test: `pwsh.exe -File C:/dev/repos/GitHub/otter/docker-build.ps1 testDebugUnitTest`
3. **If any test fails → BLOCK COMMIT**

Alternative commands:
```bash
pwsh.exe -File C:/dev/repos/GitHub/otter/docker-build.ps1 assembleDebug    # Build only
pwsh.exe -File C:/dev/repos/GitHub/otter/docker-build.ps1 testDebugUnitTest    # Test only
```

**Coverage requirement**: ≥ 80%

---

### Version Control Rules

#### Commit Format

**Format**: `#XXX: type: description`

**Examples**:
```
#1: feat: add ZIP extraction support
#2: fix: resolve path traversal vulnerability
#3: refactor: extract repository pattern
```

**Branch naming**:
- Features: `feature/#XXX-description`
- Bugfixes: `bugfix/#XXX-description`

---

### Code Quality Standards

**Mandatory rules** (see `.claude/rules/standards-*.md`):

1. **No hardcoded values** - Use constants or configuration
2. **One class/interface per file** - Single responsibility
3. **Strong typing** - Avoid `Any`, use sealed classes for state
4. **DRY principle** - No code duplication
5. **Immutability** - Prefer `val` over `var`, use data classes
6. **Null safety** - Leverage Kotlin's null-safety features
7. **Coroutines patterns** - Use structured concurrency
8. **Clean Architecture** - Respect layer boundaries (UI → Domain → Data)

---

### Security Requirements

**Critical for archive extraction**:
- ✅ **Path traversal protection** - Validate all extracted paths
- ✅ **ZIP bomb protection** - Limit file size and extraction depth
- ✅ **Storage permissions** - Handle runtime permissions correctly
- ✅ **No exposed secrets** - No hardcoded paths or tokens

**Quality Gate**:
- ✅ No new vulnerabilities
- ✅ No new bugs
- ✅ Coverage ≥ 80%
- ✅ **ALL tests passing**

**If ANY test fails** → **BLOCK COMMIT**

---

## Operational Guidelines

### Build & Test Workflow

**Commands**:
```bash
# Build debug variant
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumented tests (requires emulator/device)
./gradlew connectedDebugAndroidTest

# Lint check
./gradlew lintDebug

# Install on device
./gradlew installDebug

# Coverage (optional)
./gradlew testDebugUnitTestCoverage
```

**After any code change**:
1. Build must succeed
2. All tests must pass
3. Coverage threshold met (80%)

---

### Architecture Patterns

**MVVM + Clean Architecture** (3 layers):

1. **UI Layer** (Jetpack Compose + ViewModel)
   - Composable UI components
   - ViewModels expose StateFlow
   - Observe and react to state changes
   - Handle user interactions

2. **Domain Layer** (Pure Kotlin)
   - Use Cases (ExtractArchiveUseCase, GetExtractionProgressUseCase)
   - Repository interfaces (ArchiveRepository)
   - Domain models (ArchiveInfo, ExtractionResult, ExtractionProgress)
   - Business validation rules

3. **Data Layer** (Android-specific)
   - Repository implementations
   - Archive extractors (ZipExtractor, RpaExtractor, etc.)
   - File system operations
   - Storage management

**Design Patterns**:
- **Repository Pattern** - Abstract data sources
- **Use Case Pattern** - Single responsibility business operations
- **ViewModel Pattern** - Survive configuration changes
- **Dependency Injection** - Hilt for loose coupling
- **Sealed Classes** - Type-safe state management (UiState)

---

### Tech Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **Language** | Kotlin | 1.9.22 | Modern JVM language with null-safety |
| **Platform** | Android SDK | 26-34 | Android 8.0 to Android 14 |
| **UI** | Jetpack Compose | 1.6.1 | Declarative UI framework |
| **Design** | Material Design 3 | 1.2.0 | Modern Material Design |
| **DI** | Hilt | 2.50 | Dependency injection |
| **Async** | Coroutines | 1.7.3 | Structured concurrency |
| **Reactive** | Flow | 1.7.3 | Reactive streams |
| **Build** | Gradle KTS | 8.2.0 | Kotlin DSL build scripts |
| **Testing** | JUnit + MockK | 5.10.1 / 1.13.9 | Unit testing |

**Key Dependencies**:
```kotlin
// Core
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

// Compose
implementation(platform("androidx.compose:compose-bom:2024.01.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.activity:activity-compose:1.8.2")

// Hilt
implementation("com.google.dagger:hilt-android:2.50")
kapt("com.google.dagger:hilt-compiler:2.50")
implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// Testing
testImplementation("junit:junit:5.10.1")
testImplementation("io.mockk:mockk:1.13.9")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
```

---

## Communication Style

### Language

**Code/Documentation/Commits**: English (always)
**Conversation**: {{CONVERSATION_LANGUAGE}} (can be customized in `.claude/CLAUDE.local.md`)

---

### Decision Making

**When proposing solutions**:
1. Present 2-3 alternatives
2. List pros/cons for each
3. State recommendation with reasoning
4. Wait for user choice

**Before major changes**:
1. Analyze current code
2. Propose approach with trade-offs
3. Show impact (files affected, effort estimate)
4. Get approval before coding

---

## Project Structure

### Directory Layout

```
app/
├── src/
│   ├── main/
│   │   ├── java/app/otter/
│   │   │   ├── ui/                      # UI Layer
│   │   │   │   ├── screen/
│   │   │   │   │   └── ExtractionScreen.kt
│   │   │   │   ├── viewmodel/
│   │   │   │   │   └── ExtractionViewModel.kt
│   │   │   │   ├── state/
│   │   │   │   │   └── ExtractionUiState.kt
│   │   │   │   └── component/
│   │   │   │       └── ProgressCard.kt
│   │   │   ├── domain/                  # Domain Layer
│   │   │   │   ├── model/
│   │   │   │   │   ├── ArchiveInfo.kt
│   │   │   │   │   ├── ExtractionResult.kt
│   │   │   │   │   └── ExtractionProgress.kt
│   │   │   │   ├── repository/
│   │   │   │   │   └── ArchiveRepository.kt
│   │   │   │   └── usecase/
│   │   │   │       ├── ExtractArchiveUseCase.kt
│   │   │   │       └── GetExtractionProgressUseCase.kt
│   │   │   ├── data/                    # Data Layer
│   │   │   │   ├── repository/
│   │   │   │   │   └── ArchiveRepositoryImpl.kt
│   │   │   │   ├── extractor/
│   │   │   │   │   ├── ArchiveExtractor.kt
│   │   │   │   │   └── ZipExtractor.kt
│   │   │   │   └── storage/
│   │   │   │       └── StorageManager.kt
│   │   │   ├── di/                      # Dependency Injection
│   │   │   │   └── AppModule.kt
│   │   │   └── ExtractionActivity.kt    # Main Activity
│   │   ├── AndroidManifest.xml
│   │   └── res/                         # Resources
│   │       ├── values/
│   │       │   ├── strings.xml
│   │       │   ├── colors.xml
│   │       │   └── themes.xml
│   │       └── drawable/
│   ├── test/                            # Unit tests
│   │   └── java/app/otter/
│   │       ├── domain/
│   │       ├── data/
│   │       └── ui/
│   └── androidTest/                     # Instrumented tests
│       └── java/app/otter/
├── build.gradle.kts
└── proguard-rules.pro
```

---

### Key Files

| File | Purpose |
|------|---------|
| `AndroidManifest.xml` | App configuration, permissions, intent filters |
| `build.gradle.kts` (project) | Project-level build configuration |
| `build.gradle.kts` (app) | App module dependencies and build config |
| `ExtractionActivity.kt` | Main activity with "Open with" intent handling |
| `ExtractionViewModel.kt` | State management and business logic orchestration |
| `ExtractArchiveUseCase.kt` | Core extraction business logic |
| `ArchiveRepository.kt` | Data access abstraction |
| `ZipExtractor.kt` | ZIP extraction implementation |
| `proguard-rules.pro` | Code obfuscation and optimization rules |

---

## References

### Available Skills

- `/start-session` - Load context and offer to read GitHub issue
- `/update-context` - Update KANBAN.md, ARCHITECTURE.md, rules/
- `/analyze-commit` - Pre-commit analysis (security, quality, tests)
- `/project-setup` - Initialize .claude/ structure
- `/skill-setup` - Create or update skills

---

### Coding Standards

Located in `.claude/rules/`:

- `standards-kotlin.md` - Kotlin/Android best practices
- `standards-jetpack-compose.md` - Compose UI patterns
- `standards-testing.md` - TDD and testing guidelines
- `standards-security.md` - Security requirements (path traversal, permissions)

---

### Android-Specific Conventions

**Package Structure**:
```
app.otter.ui.*        # UI Layer (Compose, ViewModels, State)
app.otter.domain.*    # Domain Layer (Models, Use Cases, Repositories)
app.otter.data.*      # Data Layer (Repository Impl, Extractors)
app.otter.di.*        # Dependency Injection modules
```

**Naming Conventions**:
- Activities: `*Activity.kt` (e.g., `ExtractionActivity.kt`)
- ViewModels: `*ViewModel.kt` (e.g., `ExtractionViewModel.kt`)
- Composables: PascalCase (e.g., `ExtractionScreen`, `ProgressCard`)
- Use Cases: `*UseCase.kt` (e.g., `ExtractArchiveUseCase.kt`)
- Repositories: `*Repository.kt` (interface) + `*RepositoryImpl.kt` (impl)
- Sealed classes: `Ui*.kt` or `*State.kt` (e.g., `ExtractionUiState.kt`)

---

### Permissions & Security

**Runtime Permissions** (MVP):
```xml
<!-- AndroidManifest.xml -->
<!-- POST_NOTIFICATIONS for progress notifications (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- READ_EXTERNAL_STORAGE for reading archives (legacy) -->
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

**Security Validation**:
```kotlin
// Path traversal protection (CRITICAL)
private fun isValidPath(entryName: String): Boolean {
    val normalized = Paths.get(entryName).normalize().toString()
    return !normalized.startsWith("..") && !Paths.get(normalized).isAbsolute
}

// ZIP bomb protection
private fun isValidFileSize(size: Long): Boolean {
    return size <= MAX_FILE_SIZE // 100 MB
}
```

---

### Intent Filters

**"Open with" Support**:
```xml
<!-- AndroidManifest.xml -->
<activity android:name=".ExtractionActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="content" />
        <data android:mimeType="application/zip" />
        <data android:mimeType="application/x-zip-compressed" />
    </intent-filter>
</activity>
```

---

**End of Project Instructions**
