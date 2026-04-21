plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("jacoco")
    id("org.owasp.dependencycheck") version "8.4.0"
}

android {
    namespace = "app.otter"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.otter"
        minSdk = 26
        targetSdk = 34
        versionCode = 37
        versionName = "0.0.37"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }

    buildTypes {
        debug {
            isTestCoverageEnabled = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "lib7-Zip-JBinding.so"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                it.extensions.configure(JacocoTaskExtension::class.java) {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
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

    // 7-Zip for Android (supports RAR5 and all archive formats)
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03")

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

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
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
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
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
