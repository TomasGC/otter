package app.otter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PathValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var pathValidator: PathValidator

    @Before
    fun setup() {
        pathValidator = PathValidator()
    }

    @Test
    fun `should accept valid relative path`() {
        // Given
        val path = "folder/file.txt"

        // When
        val result = pathValidator.isSafePath(path)

        // Then
        assertTrue("Valid relative path should be safe", result)
    }

    @Test
    fun `should reject path with traversal pattern`() {
        // Given
        val path = "../../etc/passwd"

        // When
        val result = pathValidator.isSafePath(path)

        // Then
        assertFalse("Path with .. should be rejected", result)
    }

    @Test
    fun `should reject absolute Unix path`() {
        // Given
        val path = "/etc/passwd"

        // When
        val result = pathValidator.isSafePath(path)

        // Then
        assertFalse("Absolute Unix path should be rejected", result)
    }

    @Test
    fun `should reject absolute Windows path`() {
        // Given
        val path = "C:\\Windows\\System32\\config"

        // When
        val result = pathValidator.isSafePath(path)

        // Then
        assertFalse("Absolute Windows path should be rejected", result)
    }

    @Test
    fun `should accept path with dots in filename`() {
        // Given
        val path = "archive.v1.2.zip"

        // When
        val result = pathValidator.isSafePath(path)

        // Then
        assertTrue("Filename with dots should be safe", result)
    }

    @Test
    fun `should reject hidden path traversal`() {
        // Given
        val path = "folder/../../../etc/passwd"

        // When
        val result = pathValidator.isSafePath(path)

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
        pathValidator.validatePath(
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
            pathValidator.validatePath(
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
        val outputFile = pathValidator.createSafeOutputFile(
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
            pathValidator.createSafeOutputFile(
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
        val outputFile = pathValidator.createSafeOutputFile(
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
        val result = pathValidator.isSafePath(path)

        // Then
        assertTrue("Windows path separators should be safe", result)
    }

    @Test
    fun `should handle empty path`() {
        // Given
        val path = ""

        // When
        val result = pathValidator.isSafePath(path)

        // Then
        assertTrue("Empty path should be safe (will be rejected elsewhere)", result)
    }

    @Test
    fun `should handle Unicode characters in path`() {
        // Given
        val path = "文件/αρχείο/файл.txt"

        // When
        val result = pathValidator.isSafePath(path)

        // Then
        assertTrue("Unicode path should be safe", result)
    }

    @Test
    fun `should reject path with leading traversal`() {
        // Given
        val path = "../file.txt"

        // When
        val result = pathValidator.isSafePath(path)

        // Then
        assertFalse("Leading traversal should be rejected", result)
    }

    @Test
    fun `should reject path with trailing traversal`() {
        // Given
        val path = "folder/.."

        // When
        val result = pathValidator.isSafePath(path)

        // Then
        assertFalse("Trailing traversal should be rejected", result)
    }

    // ===== UNC and drive-relative Windows paths =====

    @Test
    fun `should reject UNC network path`() {
        val result = pathValidator.isSafePath("\\\\evil-server\\share\\payload.txt")
        assertFalse("UNC path should be rejected", result)
    }

    @Test
    fun `should reject drive-relative path without backslash`() {
        // "C:evil.txt" resolves against drive C's current directory, not the destination folder.
        val result = pathValidator.isSafePath("C:evil.txt")
        assertFalse("Drive-relative path should be rejected", result)
    }

    @Test
    fun `should throw SecurityException for UNC path via createSafeOutputFile`() {
        val destination = tempFolder.newFolder("output")

        org.junit.Assert.assertThrows(SecurityException::class.java) {
            pathValidator.createSafeOutputFile(destination, "\\\\evil-server\\share\\payload.txt")
        }
    }

    @Test
    fun `should throw SecurityException (not IOException) for drive-relative path via createSafeOutputFile`() {
        val destination = tempFolder.newFolder("output")

        org.junit.Assert.assertThrows(SecurityException::class.java) {
            pathValidator.createSafeOutputFile(destination, "C:evil.txt")
        }
    }

    @Test
    fun `validatePath wraps an unresolvable canonical path into SecurityException instead of leaking IOException`() {
        // Bypasses isSafePath directly to test validatePath's own defense-in-depth handling.
        // This defense is Windows-only: a colon mid-filename only makes File.canonicalPath throw
        // IOException on Windows (drive-relative reference). On Linux ':' is an ordinary filename
        // character, so this resolves cleanly inside destination and validatePath correctly does
        // NOT throw there — there is nothing to defend against on that platform.
        assumeTrue(System.getProperty("os.name").contains("Windows", ignoreCase = true))

        val destination = tempFolder.newFolder("output")
        val invalidFile = java.io.File(destination, "C:evil.txt")

        org.junit.Assert.assertThrows(SecurityException::class.java) {
            pathValidator.validatePath(invalidFile, destination, "C:evil.txt")
        }
    }

    @Test
    fun `should throw SecurityException for directory-name prefix bypass`() {
        // Given: dest = /tmp/base/dest, output = /tmp/base/dest-evil/passwd
        // Before fix: startsWith("/tmp/base/dest") is TRUE for "/tmp/base/dest-evil/passwd"
        // After fix:  startsWith("/tmp/base/dest/") is FALSE → SecurityException thrown
        val base = tempFolder.newFolder("base")
        val dest = java.io.File(base, "dest").apply { mkdir() }
        val evil = java.io.File(base, "dest-evil/passwd")
        evil.parentFile.mkdirs()

        try {
            pathValidator.validatePath(evil, dest, "dest-evil/passwd")
            assertTrue("Should throw SecurityException for directory-name prefix bypass", false)
        } catch (e: SecurityException) {
            assertTrue("SecurityException thrown for directory-name prefix bypass", true)
        }
    }
}
