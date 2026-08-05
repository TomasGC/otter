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
import timber.log.Timber
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for FileLoggingTree (Robolectric)
 * Tests log path generation and filename format without actual file I/O
 */
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

    private fun withTree(block: (FileLoggingTree) -> Unit) {
        val tree = FileLoggingTree(context)
        Timber.plant(tree)
        try {
            block(tree)
        } finally {
            Timber.uproot(tree)
        }
    }

    @Test
    fun `log writes DEBUG message with D priority char`() = withTree { tree ->
        Timber.tag("TestTag").d("Debug message")
        val content = File(tree.getLogPath()).readText()
        assertTrue(content.contains("Debug message"))
        assertTrue(content.contains("D/TestTag"))
    }

    @Test
    fun `log writes INFO message with I priority char`() = withTree { tree ->
        Timber.tag("Tag").i("Info message")
        assertTrue(File(tree.getLogPath()).readText().contains("I/Tag"))
    }

    @Test
    fun `log writes WARN message with W priority char`() = withTree { tree ->
        Timber.tag("Tag").w("Warn message")
        assertTrue(File(tree.getLogPath()).readText().contains("W/Tag"))
    }

    @Test
    fun `log writes ERROR message with E priority char`() = withTree { tree ->
        Timber.tag("Tag").e("Error message")
        assertTrue(File(tree.getLogPath()).readText().contains("E/Tag"))
    }

    @Test
    fun `log writes VERBOSE message with V priority char`() = withTree { tree ->
        Timber.tag("Tag").v("Verbose message")
        assertTrue(File(tree.getLogPath()).readText().contains("V/Tag"))
    }

    @Test
    fun `log appends throwable stack trace when throwable is not null`() = withTree { tree ->
        val exception = RuntimeException("test error")
        Timber.tag("Tag").e(exception, "Error with throwable")
        val content = File(tree.getLogPath()).readText()
        assertTrue(content.contains("Error with throwable"))
        assertTrue(content.contains("RuntimeException"))
    }
}
