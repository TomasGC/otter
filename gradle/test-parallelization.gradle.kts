/**
 * Test Parallelization Configuration
 *
 * Configures Gradle test tasks to run in parallel with optimized settings.
 * Uses maxParallelForks to distribute tests across CPU cores for faster execution.
 *
 * IMPORTANT: Robolectric tests have issues with parallel execution (deadlocks/timeouts).
 * Keeping maxParallelForks = 1 until Robolectric 5.0+ is stable with parallelization.
 *
 * Note: Gradle automatically handles test result isolation per fork.
 * No manual XML directory separation needed.
 */

tasks.withType<Test> {
    // Disable parallel execution due to Robolectric compatibility issues
    // TODO: Re-enable with maxParallelForks = Runtime.getRuntime().availableProcessors() after Robolectric upgrade
    maxParallelForks = 1

    // Configure reports
    reports {
        junitXml.required.set(true)
        html.required.set(true)
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
