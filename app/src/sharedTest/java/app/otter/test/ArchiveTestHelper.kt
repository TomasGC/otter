package app.otter.test

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Helper for creating test archives and managing test files.
 * Shared between unit tests (JVM) and instrumented tests (Android).
 */
object ArchiveTestHelper {

    /**
     * Creates a temporary directory for test output.
     * Directory is automatically deleted when test ends.
     */
    fun createTempTestDirectory(prefix: String = "otter-test"): File {
        return Files.createTempDirectory(prefix).toFile().apply {
            deleteOnExit()
        }
    }

    /**
     * Creates a simple ZIP archive for testing.
     *
     * @param outputFile Destination file for the ZIP
     * @param entries Map of filename to content
     */
    fun createZipArchive(outputFile: File, entries: Map<String, String>) {
        outputFile.parentFile?.mkdirs()

        ZipOutputStream(outputFile.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    /**
     * Creates a ZIP archive with nested folder structure.
     *
     * @param outputFile Destination file for the ZIP
     */
    fun createZipArchiveWithFolders(outputFile: File) {
        val entries = mapOf(
            "root_file.txt" to "Root file content",
            "folder1/file1.txt" to "File 1 in folder1",
            "folder1/file2.txt" to "File 2 in folder1",
            "folder2/file3.txt" to "File 3 in folder2",
            "folder1/nested/deep_file.txt" to "Deep nested file"
        )
        createZipArchive(outputFile, entries)
    }

    /**
     * Verifies that extracted files match expected structure.
     *
     * @param extractedDir Directory where files were extracted
     * @param expectedFiles List of expected relative file paths
     */
    fun assertExtractedFilesExist(extractedDir: File, expectedFiles: List<String>) {
        val missingFiles = expectedFiles.filter { path ->
            !File(extractedDir, path).exists()
        }

        if (missingFiles.isNotEmpty()) {
            throw AssertionError(
                "Missing extracted files:\n${missingFiles.joinToString("\n") { "  - $it" }}"
            )
        }
    }

    /**
     * Verifies that a file contains expected content.
     *
     * @param file File to check
     * @param expectedContent Expected content
     */
    fun assertFileContent(file: File, expectedContent: String) {
        if (!file.exists()) {
            throw AssertionError("File does not exist: ${file.absolutePath}")
        }

        val actualContent = file.readText()
        if (actualContent != expectedContent) {
            throw AssertionError(
                "File content mismatch:\nExpected: $expectedContent\nActual: $actualContent"
            )
        }
    }

    /**
     * Recursively lists all files in a directory.
     *
     * @param dir Directory to list
     * @return List of relative file paths
     */
    fun listAllFiles(dir: File): List<String> {
        if (!dir.exists()) return emptyList()

        return dir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(dir).path }
            .sorted()
            .toList()
    }

    /**
     * Cleans up a directory and all its contents.
     */
    fun cleanupDirectory(dir: File) {
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }
}
