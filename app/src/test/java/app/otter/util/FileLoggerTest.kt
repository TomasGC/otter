package app.otter.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var destinationFolder: File

    @Before
    fun setup() {
        destinationFolder = tempFolder.newFolder("extraction")
    }

    @After
    fun tearDown() {
        FileLogger.close()
    }

    @Test
    fun `should initialize logger with correct file name`() {
        // Given
        val archiveName = "test_archive.zip"

        // When
        FileLogger.initialize(destinationFolder, archiveName)

        // Then
        val logFilePath = FileLogger.getLogFilePath()
        assertNotNull("Log file path should not be null", logFilePath)
        assertTrue("Log file should exist", File(logFilePath!!).exists())
        assertTrue("Log file name should contain archive name", logFilePath.contains("test_archive"))
        assertTrue("Log file should be .txt", logFilePath.endsWith("_extraction.txt"))
    }

    @Test
    fun `should write log messages to file`() {
        // Given
        FileLogger.initialize(destinationFolder, "test.zip")
        val logFilePath = FileLogger.getLogFilePath()!!

        // When
        FileLogger.log("Test message 1")
        FileLogger.log("Test message 2", "CustomTag")
        FileLogger.close()

        // Then
        val logContent = File(logFilePath).readText()
        assertTrue("Log should contain first message", logContent.contains("Test message 1"))
        assertTrue("Log should contain second message", logContent.contains("Test message 2"))
        assertTrue("Log should contain custom tag", logContent.contains("[CustomTag]"))
        assertTrue("Log should contain default tag", logContent.contains("[Otter]"))
    }

    @Test
    fun `should write error messages to file`() {
        // Given
        FileLogger.initialize(destinationFolder, "test.zip")
        val logFilePath = FileLogger.getLogFilePath()!!
        val exception = RuntimeException("Test exception")

        // When
        FileLogger.logError("Error message", exception, "ErrorTag")
        FileLogger.close()

        // Then
        val logContent = File(logFilePath).readText()
        assertTrue("Log should contain error message", logContent.contains("ERROR: Error message"))
        assertTrue("Log should contain exception message", logContent.contains("Test exception"))
        assertTrue("Log should contain error tag", logContent.contains("[ErrorTag]"))
    }

    @Test
    fun `should handle error without throwable`() {
        // Given
        FileLogger.initialize(destinationFolder, "test.zip")
        val logFilePath = FileLogger.getLogFilePath()!!

        // When
        FileLogger.logError("Error without exception")
        FileLogger.close()

        // Then
        val logContent = File(logFilePath).readText()
        assertTrue("Log should contain error message", logContent.contains("ERROR: Error without exception"))
    }

    @Test
    fun `should append to existing log file`() {
        // Given
        FileLogger.initialize(destinationFolder, "test.zip")
        FileLogger.log("First message")
        FileLogger.close()

        // When - Reinitialize and log again
        FileLogger.initialize(destinationFolder, "test.zip")
        FileLogger.log("Second message")
        FileLogger.close()

        // Then
        val logFilePath = FileLogger.getLogFilePath()!!
        val logContent = File(logFilePath).readText()
        assertTrue("Log should contain first message", logContent.contains("First message"))
        assertTrue("Log should contain second message", logContent.contains("Second message"))
    }

    @Test
    fun `should handle archive name without extension`() {
        // Given
        val archiveName = "archive_no_extension"

        // When
        FileLogger.initialize(destinationFolder, archiveName)

        // Then
        val logFilePath = FileLogger.getLogFilePath()
        assertNotNull("Log file path should not be null", logFilePath)
        assertTrue("Log file name should contain archive name", logFilePath!!.contains("archive_no_extension"))
    }

    @Test
    fun `should extract archive name correctly with multiple dots`() {
        // Given
        val archiveName = "my.archive.v1.2.zip"

        // When
        FileLogger.initialize(destinationFolder, archiveName)

        // Then
        val logFilePath = FileLogger.getLogFilePath()
        assertNotNull("Log file path should not be null", logFilePath)
        assertTrue("Log file name should handle multiple dots", logFilePath!!.contains("my.archive.v1.2"))
    }

    @Test
    fun `should place log file in parent directory`() {
        // Given
        val archiveName = "test.zip"

        // When
        FileLogger.initialize(destinationFolder, archiveName)

        // Then
        val logFilePath = FileLogger.getLogFilePath()!!
        val logFile = File(logFilePath)
        assertEquals(
            "Log file should be in parent directory",
            destinationFolder.parentFile?.absolutePath,
            logFile.parentFile?.absolutePath,
        )
    }

    @Test
    fun `should include timestamp in log messages`() {
        // Given
        FileLogger.initialize(destinationFolder, "test.zip")
        val logFilePath = FileLogger.getLogFilePath()!!

        // When
        FileLogger.log("Timestamped message")
        FileLogger.close()

        // Then
        val logContent = File(logFilePath).readText()
        // Timestamp format: HH:mm:ss.SSS (e.g., 14:30:25.123)
        assertTrue(
            "Log should contain timestamp",
            logContent.contains("Timestamped message") &&
                Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}").containsMatchIn(logContent),
        )
    }

    @Test
    fun `should return log file path after initialization`() {
        // When
        FileLogger.initialize(destinationFolder, "test.zip")
        val logFilePath = FileLogger.getLogFilePath()

        // Then
        assertNotNull("Log file path should not be null after initialization", logFilePath)
    }

    @Test
    fun `should handle special characters in archive name`() {
        // Given
        val archiveName = "my archive (2024) [v1].zip"

        // When
        FileLogger.initialize(destinationFolder, archiveName)

        // Then
        val logFilePath = FileLogger.getLogFilePath()
        assertNotNull("Log file path should not be null", logFilePath)
        assertTrue("Log file should exist", File(logFilePath!!).exists())
    }

    @Test
    fun `should flush and close writer properly`() {
        // Given
        FileLogger.initialize(destinationFolder, "test.zip")
        val logFilePath = FileLogger.getLogFilePath()!!
        FileLogger.log("Message before close")

        // When
        FileLogger.close()

        // Then
        val logContent = File(logFilePath).readText()
        assertTrue("Log should be flushed before close", logContent.contains("Message before close"))
    }
}
