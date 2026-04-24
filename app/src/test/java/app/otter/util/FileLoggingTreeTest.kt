package app.otter.util

import android.content.Context
import android.os.Environment
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for FileLoggingTree (Robolectric)
 * Tests log path generation and filename format without actual file I/O
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileLoggingTreeTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        tempDir = createTempDirectory("otter-test-logs").toFile()
        every { context.cacheDir } returns tempDir

        mockkStatic(Environment::class)
        every { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) } returns tempDir
    }

    @After
    fun teardown() {
        unmockkAll()
        tempDir.deleteRecursively()
    }

    @Test
    fun `FileLoggingTree should be created successfully`() {
        // When
        val tree = FileLoggingTree(context)

        // Then
        val logPath = tree.getLogPath()
        assertTrue("Log path should not be empty", logPath.isNotEmpty())
        assertTrue("Log path should contain otter-log prefix", logPath.contains("otter-log-"))
        assertTrue("Log path should be in temp directory", logPath.startsWith(tempDir.absolutePath))
    }

    @Test
    fun `getLogPath should return path ending with txt`() {
        // When
        val tree = FileLoggingTree(context)
        val logPath = tree.getLogPath()

        // Then
        assertTrue("Path should end with .txt", logPath.endsWith(".txt"))
    }

    @Test
    fun `log file name should include timestamp format`() {
        // Given & When
        val tree = FileLoggingTree(context)
        val logPath = tree.getLogPath()
        val fileName = File(logPath).name

        // Then
        assertTrue("Filename should start with otter-log-", fileName.startsWith("otter-log-"))
        assertTrue("Filename should end with .txt", fileName.endsWith(".txt"))

        // Check timestamp format (yyyy-MM-dd-HH-mm)
        val timestampPart = fileName.removePrefix("otter-log-").removeSuffix(".txt")
        val regex = """\d{4}-\d{2}-\d{2}-\d{2}-\d{2}""".toRegex()
        assertTrue("Filename should contain valid timestamp", regex.matches(timestampPart))
    }
}
