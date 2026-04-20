package app.otter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PathValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `should accept valid relative path`() {
        // Given
        val path = "folder/file.txt"

        // When
        val result = PathValidator.isSafePath(path)

        // Then
        assertTrue("Valid relative path should be safe", result)
    }

    @Test
    fun `should reject path with traversal pattern`() {
        // Given
        val path = "../../etc/passwd"

        // When
        val result = PathValidator.isSafePath(path)

        // Then
        assertFalse("Path with .. should be rejected", result)
    }

    @Test
    fun `should reject absolute Unix path`() {
        // Given
        val path = "/etc/passwd"

        // When
        val result = PathValidator.isSafePath(path)

        // Then
        assertFalse("Absolute Unix path should be rejected", result)
    }

    @Test
    fun `should reject absolute Windows path`() {
        // Given
        val path = "C:\\Windows\\System32\\config"

        // When
        val result = PathValidator.isSafePath(path)

        // Then
        assertFalse("Absolute Windows path should be rejected", result)
    }

    @Test
    fun `should accept path with dots in filename`() {
        // Given
        val path = "archive.v1.2.zip"

        // When
        val result = PathValidator.isSafePath(path)

        // Then
        assertTrue("Filename with dots should be safe", result)
    }

    @Test
    fun `should reject hidden path traversal`() {
        // Given
        val path = "folder/../../../etc/passwd"

        // When
        val result = PathValidator.isSafePath(path)

        // Then
        assertFalse("Hidden traversal should be rejected", result)
    }

    @Test
    fun `should validate output file within destination`() {
        // Given
        val destination = tempFolder.newFolder("output")
        val outputFile = tempFolder.newFile("output/file.txt")
        val entryName = "file.txt"

        // When/Then - Should not throw
        PathValidator.validatePath(
            outputFile,
            destination,
            entryName,
        )
    }

    @Test
    fun `should throw SecurityException for file outside destination`() {
        // Given
        val destination = tempFolder.newFolder("output")
        val outsideFile = tempFolder.newFile("outside.txt")
        val entryName = "../outside.txt"

        // When/Then
        try {
            PathValidator.validatePath(
                outsideFile,
                destination,
                entryName,
            )
            assertTrue("Should throw SecurityException", false)
        } catch (e: SecurityException) {
            assertTrue("Error should mention entry name", e.message?.contains(entryName) ?: false)
        }
    }

    @Test
    fun `should create safe output file with parent directories`() {
        // Given
        val destination = tempFolder.newFolder("output")
        val entryName = "folder1/folder2/file.txt"

        // When
        val outputFile = PathValidator.createSafeOutputFile(
            destination,
            entryName,
        )

        // Then
        assertTrue("Output file should exist", outputFile.parentFile?.exists() ?: false)
        assertEquals(
            "File should be in correct location",
            destination.absolutePath,
            outputFile.parentFile?.parentFile?.parentFile?.absolutePath,
        )
    }

    @Test
    fun `should throw SecurityException for traversal in createSafeOutputFile`() {
        // Given
        val destination = tempFolder.newFolder("output")
        val entryName = "../../../etc/passwd"

        // When/Then
        try {
            PathValidator.createSafeOutputFile(
            destination,
            entryName,
        )
            assertTrue("Should throw SecurityException", false)
        } catch (e: SecurityException) {
            assertTrue(
                "Error should mention entry outside destination",
                e.message?.contains("outside") ?: false,
            )
        }
    }

    @Test
    fun `should handle nested directories in createSafeOutputFile`() {
        // Given
        val destination = tempFolder.newFolder("output")
        val entryName = "a/b/c/d/e/file.txt"

        // When
        val outputFile = PathValidator.createSafeOutputFile(
            destination,
            entryName,
        )

        // Then
        assertTrue("All parent directories should be created", outputFile.parentFile?.exists() ?: false)
    }

    @Test
    fun `should accept Windows-style path separators`() {
        // Given
        val path = "folder\\subfolder\\file.txt"

        // When
        val result = PathValidator.isSafePath(path)

        // Then
        assertTrue("Windows path separators should be safe", result)
    }

    @Test
    fun `should handle empty path`() {
        // Given
        val path = ""

        // When
        val result = PathValidator.isSafePath(path)

        // Then
        assertTrue("Empty path should be safe (will be rejected elsewhere)", result)
    }

    @Test
    fun `should handle Unicode characters in path`() {
        // Given
        val path = "文件/αρχείο/файл.txt"

        // When
        val result = PathValidator.isSafePath(path)

        // Then
        assertTrue("Unicode path should be safe", result)
    }

    @Test
    fun `should reject path with leading traversal`() {
        // Given
        val path = "../file.txt"

        // When
        val result = PathValidator.isSafePath(path)

        // Then
        assertFalse("Leading traversal should be rejected", result)
    }

    @Test
    fun `should reject path with trailing traversal`() {
        // Given
        val path = "folder/.."

        // When
        val result = PathValidator.isSafePath(path)

        // Then
        assertFalse("Trailing traversal should be rejected", result)
    }
}
