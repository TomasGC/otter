plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("jacoco")
    id("org.owasp.dependencycheck") version "8.4.0"
    id("io.gitlab.arturbosch.detekt")
}

jacoco {
    toolVersion = "0.8.11"
}

android {
    namespace = "app.otter"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.otter"
        minSdk = 26
        targetSdk = 34
        versionCode = 116
        versionName = "0.0.116"

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
    }

    buildTypes {
        debug {
            isTestCoverageEnabled = true
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
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
                // Configure JaCoCo for unit tests with standard path
                it.extensions.configure(JacocoTaskExtension::class.java) {
                    isEnabled = true
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                    // Use standard JaCoCo path instead of Android's default
                    destinationFile = file("${buildDir}/jacoco/${it.name}.exec")
                }
                // Enable test output logging to see println() statements
                it.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showStandardStreams = true
                }
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // DocumentFile for accessing parent folder
    implementation("androidx.documentfile:documentfile:1.0.1")

    // 7-Zip for Android (supports RAR5, 7z)
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03")

    // Apache Commons Compress (supports GZIP, TAR, TAR.GZ, TGZ)
    implementation("org.apache.commons:commons-compress:1.25.0")

    // Timber - Logging library
    implementation("com.jakewharton.timber:timber:5.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.robolectric:robolectric:4.11.1")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("io.mockk:mockk-android:1.13.9")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.50")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.50")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
}

// JaCoCo unit test coverage report
tasks.register<JacocoReport>("jacocoTestDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        xml.outputLocation.set(file("${buildDir}/reports/jacoco/testDebugUnitTestReport/testDebugUnitTestReport.xml"))
        html.outputLocation.set(file("${buildDir}/reports/jacoco/testDebugUnitTestReport/html"))
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*_Hilt*.class",
        "**/*_Factory.class",
        "**/*_MembersInjector.class",
        "**/Hilt_*.class",
        // Android components (difficult to test, will add tests later)
        "**/ExtractionService.class",
        "**/ExtractionActivity.class",
        "**/OtterApplication.class",
        "**/NotificationHelper.class",
        // Base class with protected logging methods (tested via concrete implementations)
        "**/BaseArchiveExtractor.class",
        "**/BaseArchiveExtractor$*.class"
    )

    val mainSrc = files("${project.projectDir}/src/main/java")
    val kotlinDebugTree = fileTree("${project.buildDir}/tmp/kotlin-classes/debug")
    val javaDebugTree = fileTree("${project.buildDir}/intermediates/javac/debug/classes")

    sourceDirectories.setFrom(mainSrc)
    classDirectories.setFrom(
        files(
            kotlinDebugTree.matching { exclude(fileFilter) },
            javaDebugTree.matching { exclude(fileFilter) }
        )
    )

    executionData.setFrom(files("${buildDir}/jacoco/testDebugUnitTest.exec"))
}

// Jacoco merged coverage report (unit + instrumented tests)
tasks.register<JacocoReport>("jacocoMergedReport") {
    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        xml.outputLocation.set(file("${buildDir}/reports/jacoco/jacocoMergedReport/jacocoMergedReport.xml"))
        html.outputLocation.set(file("${buildDir}/reports/jacoco/jacocoMergedReport/html"))
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*_Hilt*.class",
        "**/*_Factory.class",
        "**/*_MembersInjector.class",
        "**/Hilt_*.class",
        // Android components (difficult to test, will add tests later)
        "**/ExtractionService.class",
        "**/ExtractionActivity.class",
        "**/OtterApplication.class",
        "**/NotificationHelper.class",
        // Base class with protected logging methods (tested via concrete implementations)
        "**/BaseArchiveExtractor.class",
        "**/BaseArchiveExtractor$*.class"
    )

    val mainSrc = files("${project.projectDir}/src/main/java")

    val kotlinDebugTree = fileTree("${project.buildDir}/tmp/kotlin-classes/debug")
    val javaDebugTree = fileTree("${project.buildDir}/intermediates/javac/debug/classes")

    sourceDirectories.setFrom(mainSrc)
    classDirectories.setFrom(
        files(
            kotlinDebugTree.matching { exclude(fileFilter) },
            javaDebugTree.matching { exclude(fileFilter) }
        )
    )

    // Merge execution data from both unit and instrumented tests
    executionData.setFrom(fileTree(buildDir) {
        include(
            "jacoco/testDebugUnitTest.exec",
            "outputs/code_coverage/debugAndroidTest/connected/**/*.ec"
        )
    })
}

// Dependency check configuration
dependencyCheck {
    analyzers.assemblyEnabled = false
    failBuildOnCVSS = 7.0f
    suppressionFile = file("dependency-check-suppressions.xml").absolutePath
}

// Detekt configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
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
