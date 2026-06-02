tasks.register("grantTestStoragePermissions") {
    group = "verification"
    description = "Grant storage permissions to test APK"

    doLast {
        println("🔑 Granting storage permissions to test APK...")

        val permissions = listOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )

        permissions.forEach { permission ->
            val process = ProcessBuilder("adb", "shell", "pm", "grant", "app.otter.test", permission)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                println("⚠️  Warning: Could not grant $permission (may already be granted)")
                println(output)
            } else {
                println("✅ Granted $permission")
            }
        }
    }
}

tasks.register("sendTestArchivesToDevice") {
    group = "verification"
    description = "Send test archives to Android device before running instrumented tests"

    dependsOn("grantTestStoragePermissions")

    doLast {
        println("📦 Sending test archives to device...")

        val pythonScript = File(project.rootDir, "scripts/src/cli/send_to_phone.py")
        if (!pythonScript.exists()) {
            throw GradleException("send_to_phone.py not found at ${pythonScript.absolutePath}. Expected at scripts/src/cli/send_to_phone.py")
        }

        val process = ProcessBuilder("python", pythonScript.absolutePath, "--ci")
            .directory(project.rootDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        println(output)

        if (exitCode != 0) {
            throw GradleException("Failed to send test archives (exit code: $exitCode)")
        }
    }
}

// Hook into connectedDebugAndroidTest lifecycle
// The sequence should be:
// 1. installDebug (install main app)
// 2. installDebugAndroidTest (install test APK)
// 3. grantTestStoragePermissions (grant permissions)
// 4. sendTestArchivesToDevice (send archives to /storage/emulated/0/otter-test-archives)
// 5. connectedDebugAndroidTest (run tests)
//
// Note: We use /storage/emulated/0/otter-test-archives instead of app private directory
// because uninstall clears /sdcard/Android/data/app.otter.test/

afterEvaluate {
    tasks.named("connectedDebugAndroidTest") {
        // Ensure main app and test APK are installed before sending archives
        dependsOn("installDebug", "installDebugAndroidTest", "sendTestArchivesToDevice")
    }

    tasks.named("sendTestArchivesToDevice") {
        // Must run after BOTH main app and test APK are installed
        mustRunAfter("installDebug", "installDebugAndroidTest")
    }
}
