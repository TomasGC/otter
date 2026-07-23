plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("org.jetbrains.kotlinx.kover")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "app.otter"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.otter"
        minSdk = 26
        targetSdk = 35
        versionCode = 255
        versionName = "0.0.255"

        testInstrumentationRunner = "app.otter.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("../keystore/release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "aBaEtUAPd9KGvmaYQFqFUacjbTg="
            keyAlias = "otter-release"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "k0/tuOtQvGzrvBttMmBOmEHHk7g="
        }
    }

    sourceSets {
        // Force inclusion of test archive files in androidTest assets
        getByName("androidTest") {
            assets.srcDirs("src/androidTest/assets")
        }

        // Shared test code between unit tests (test/) and instrumented tests (androidTest/)
        getByName("test") {
            java.srcDir("src/sharedTest/java")
        }

        getByName("androidTest") {
            java.srcDir("src/sharedTest/java")
        }
    }

    buildTypes {
        debug {
            // Kover handles coverage automatically, no need for explicit flags
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "lib7-Zip-JBinding.so"
        }
    }

    // Force inclusion of test archive files in androidTest APK
    androidResources {
        noCompress += listOf("tar", "gz", "tgz", "tar.gz", "7z", "rar", "zip", "rpa")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2048m"
                it.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showStandardStreams = true
                }
                // Archives directory for tests that need real archive files
                it.systemProperty("archives.dir", rootProject.rootDir.absolutePath + "/archives")
            }
        }
        managedDevices {
            devices {
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel4api30").apply {
                    device = "Pixel 4"
                    apiLevel = 30
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // DocumentFile for accessing parent folder
    implementation("androidx.documentfile:documentfile:1.0.1")

    // 7-Zip for Android (supports RAR5, 7z)
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03")

    // Apache Commons Compress (supports GZIP, TAR, TAR.GZ, TGZ)
    implementation("org.apache.commons:commons-compress:1.27.1")

    // Timber - Logging library
    implementation("com.jakewharton.timber:timber:5.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("io.mockk:mockk-android:1.13.12")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.52")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
}

// Kover configuration (coverage tool optimized for Kotlin + Robolectric)
// NOTE: koverReport { filters {} } at root scope applies only to JVM/default variant.
// For Android build variant tasks (koverXmlReportDebug), must use androidReports("debug").
koverReport {
    androidReports("debug") {
        filters {
            excludes {
                classes(
                    // Generated boilerplate
                    "**.R",
                    "**.R$*",
                    "**.BuildConfig",
                    "**.Manifest*",
                    "**.*Test*",
                    // Android framework (not compiled into app JAR)
                    "android.*",
                    "androidx.*",
                    // Hilt-generated: factories, injectors, instance holders
                    "**.*_Hilt*",
                    "**.*_Factory",
                    "**.*_Factory$*",
                    "**.*_MembersInjector",
                    "**.Hilt_*",
                    // Hilt DI modules — pure @Provides wiring, no testable logic
                    "**.AppModule",
                    "app.otter.di.AppModule_*",
                    "**.CoroutineModule",
                    "app.otter.di.CoroutineModule_*",
                    // Hilt aggregated roots / generated injectors
                    "dagger.hilt.internal.aggregatedroot.codegen.*",
                    "hilt_aggregated_deps.*",
                    // Android entry points: only testable via instrumented tests
                    "**.ExtractionService",
                    "**.ExtractionService$*",
                    "**.ExtractionActivity",
                    "**.ExtractionActivity$*",
                    "**.BrowserActivity",
                    "**.BrowserActivity$*",
                    "**.OtterApplication",
                    // Compose UI + theme: only testable via instrumented Compose tests
                    "app.otter.ui.screen.*",
                    "app.otter.ui.component.*",
                    "app.otter.ui.theme.*",
                    // Abstract base: no standalone instantiable bytecode
                    "**.BaseArchiveExtractor",
                    "**.BaseArchiveExtractor$*"
                )
            }
        }

        verify {
            rule {
                minBound(80)
            }
        }
    }
}

// Detekt configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    source.setFrom("$projectDir/src/main/java")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }
}

// Apply test parallelization configuration
apply(from = "../gradle/test-parallelization.gradle.kts")

// Apply test constants generation script
apply(from = "../gradle/generate-test-constants.gradle.kts")

// Apply test archives sending script
apply(from = "../gradle/send-test-archives.gradle.kts")

// Filter testDebugUnitTest by stage via -DtestType=<value>
// Used by CI for parallel/sequential test stages.
// Must be in afterEvaluate: Android plugin registers testDebugUnitTest during evaluation.
afterEvaluate {
tasks.named("testDebugUnitTest", Test::class.java) {
    val testType = System.getProperty("testType", "all")
    when (testType) {
        // Unit - domain + service + util (exclude integration tests sharing same packages)
        "unit-domain-service" -> filter {
            includeTestsMatching("app.otter.domain.*")
            includeTestsMatching("app.otter.service.*")
            includeTestsMatching("app.otter.util.*")
            excludeTestsMatching("*IntegrationTest*")
            excludeTestsMatching("*RealIntegrationTest*")
        }
        // Unit - data layer (browser, inspector, repository, util + 4 unit extractor tests)
        // Note: app.otter.data.extractor is shared with integration tests, use class names for unit
        "unit-data" -> filter {
            includeTestsMatching("app.otter.data.browser.*")
            includeTestsMatching("app.otter.data.inspector.*")
            includeTestsMatching("app.otter.data.repository.*")
            includeTestsMatching("app.otter.data.util.*")
            includeTestsMatching("*BaseArchiveExtractorTest*")
            includeTestsMatching("*RpaArchiveCreationTest*")
            includeTestsMatching("*RpaExtractorParseTest*")
            includeTestsMatching("*RpaHexDumpTest*")
        }
        // Unit - UI / ViewModel
        "unit-ui" -> filter {
            includeTestsMatching("app.otter.ui.*")
        }
        // Integration mock - extractor tests (ZIP, RAR, TAR, 7z, RPA against real archives)
        "integration-mock-extractor" -> filter {
            includeTestsMatching("app.otter.data.extractor.*")
            excludeTestsMatching("*BaseArchiveExtractorTest*")
            excludeTestsMatching("*RpaArchiveCreationTest*")
            excludeTestsMatching("*RpaExtractorParseTest*")
            excludeTestsMatching("*RpaHexDumpTest*")
            excludeTestsMatching("*RealIntegrationTest*")
        }
        // Integration mock - service, viewmodel, domain usecase
        "integration-mock-other" -> filter {
            includeTestsMatching("app.otter.integration.data.browser.*")
            includeTestsMatching("app.otter.integration.service.*")
            includeTestsMatching("app.otter.integration.viewmodel.*")
            includeTestsMatching("app.otter.domain.usecase.ExtractSelectedItemsIntegrationTest")
        }
        // Integration real - no mocks at all
        "integration-real" -> filter {
            includeTestsMatching("*RealIntegrationTest*")
        }
        // "all" → no filter, runs everything (default for local development)
    }
}
}
