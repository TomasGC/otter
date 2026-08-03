/**
 * Test configuration for all test tasks.
 *
 * CI parallelism is achieved via separate Gradle test stages running on separate runners,
 * not via maxParallelForks (net regression on 2-core CI runners due to Robolectric overhead).
 */

tasks.withType<Test> {
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
