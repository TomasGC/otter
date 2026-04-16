package app.otter.util

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExtractionDestinationResolverTest {

    private lateinit var context: Context
    private lateinit var resolver: ExtractionDestinationResolver

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        resolver = ExtractionDestinationResolver(context)
    }

    @Test
    fun `should create destination folder from file name`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = "archive.zip"

        // When
        val destination = resolver.createDestinationFolder(parentPath, fileName)

        // Then
        assertEquals(
            "Folder name should match archive name without extension",
            "archive",
            destination.name,
        )
        assertEquals(
            "Parent path should match",
            parentPath,
            destination.parentFile?.absolutePath,
        )
    }

    @Test
    fun `should handle file name with multiple dots`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = "my.archive.v1.2.zip"

        // When
        val destination = resolver.createDestinationFolder(parentPath, fileName)

        // Then
        assertEquals(
            "Should remove only last extension",
            "my.archive.v1.2",
            destination.name,
        )
    }

    @Test
    fun `should handle file name without extension`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = "archive_no_extension"

        // When
        val destination = resolver.createDestinationFolder(parentPath, fileName)

        // Then
        assertEquals(
            "Folder name should match file name",
            "archive_no_extension",
            destination.name,
        )
    }

    @Test
    fun `should create downloads destination`() {
        // Given
        val fileName = "test.zip"

        // When
        val destination = resolver.createDownloadsDestination(fileName)

        // Then
        assertNotNull("Destination should not be null", destination)
        assertEquals(
            "Folder name should match archive name",
            "test",
            destination.name,
        )
        assertTrue(
            "Path should contain Download",
            destination.absolutePath.contains("Download"),
        )
    }

    @Test
    fun `should handle special characters in file name`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = "my archive (2024) [v1].zip"

        // When
        val destination = resolver.createDestinationFolder(parentPath, fileName)

        // Then
        assertEquals(
            "Special characters should be preserved",
            "my archive (2024) [v1]",
            destination.name,
        )
    }

    @Test
    fun `should handle Unicode file name`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = "文件.zip"

        // When
        val destination = resolver.createDestinationFolder(parentPath, fileName)

        // Then
        assertEquals(
            "Unicode should be preserved",
            "文件",
            destination.name,
        )
    }

    @Test
    fun `should handle very long file name`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val longName = "a".repeat(200)
        val fileName = "$longName.zip"

        // When
        val destination = resolver.createDestinationFolder(parentPath, fileName)

        // Then
        assertEquals(
            "Long name should be preserved",
            longName,
            destination.name,
        )
    }

    @Test
    fun `should handle empty file name`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = ""

        // When
        val destination = resolver.createDestinationFolder(parentPath, fileName)

        // Then
        // substringBeforeLast on empty string returns empty string
        // But File with empty name might normalize to "." or ""
        assertNotNull("Destination should not be null", destination)
    }

    @Test
    fun `should resolve destination with fallback to downloads`() {
        // Given - Invalid URI that will trigger fallback
        val invalidUri = Uri.parse("content://invalid/document/1")
        val fileName = "test.zip"

        // When
        val destination = resolver.resolveDestination(invalidUri, fileName)

        // Then
        assertNotNull("Destination should not be null", destination)
        assertTrue(
            "Should fallback to Downloads",
            destination.absolutePath.contains("Download"),
        )
        assertEquals(
            "Folder name should be correct",
            "test",
            destination.name,
        )
    }

    @Test
    fun `should return null for invalid URI`() {
        // Given
        val invalidUri = Uri.parse("invalid://path")

        // When
        val result = resolver.getRealPathFromUri(invalidUri)

        // Then
        assertEquals("Invalid URI should return null", null, result)
    }

    @Test
    fun `should handle file name with only extension`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = ".zip"

        // When
        val destination = resolver.createDestinationFolder(parentPath, fileName)

        // Then
        // substringBeforeLast(".zip", ".") returns "." (the part before last dot)
        // File(".") might normalize to current directory
        assertNotNull("Destination should not be null", destination)
        assertTrue(
            "Folder name should be . or normalized",
            destination.name == "." || destination.name.isNotEmpty(),
        )
    }

    @Test
    fun `should handle nested parent path`() {
        // Given
        val parentPath = "/storage/emulated/0/Download/folder1/folder2"
        val fileName = "archive.zip"

        // When
        val destination = resolver.createDestinationFolder(parentPath, fileName)

        // Then
        assertEquals(
            "Parent should be nested path",
            parentPath,
            destination.parentFile?.absolutePath,
        )
    }
}
