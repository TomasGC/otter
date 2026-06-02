package app.otter.data.inspector

import app.otter.domain.inspector.ArchiveEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests for ZipInspector path filtering and Unicode handling.
 *
 * Phase 4: Path Filtering Tests
 * - Task #73: Directory selection expansion tests
 * - Task #74: Unicode path tests
 */
class ZipInspectorPathTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ========== Task #73: Directory Selection Expansion Tests ==========

    @Test
    fun `selecting directory should include all files in directory`() = runTest {
        // Arrange - Create ZIP with directory structure
        val zipFile = createTestZip(mapOf(
            "docs/" to "",
            "docs/readme.txt" to "readme content",
            "docs/guide.txt" to "guide content",
            "src/main.kt" to "code"
        ))

        val inspector = ZipInspector(zipFile)

        // Act - Get all entries
        val entries = inspector.entries().toList()

        // Assert - Directory and its files are present
        val docsEntries = entries.filter { it.path.startsWith("docs/") }
        assertTrue("Should have docs entries", docsEntries.isNotEmpty())

        // Directory itself
        val docsDir = entries.find { it.path == "docs/" }
        assertTrue("Should have docs/ directory", docsDir != null)
        assertTrue("Directory should be marked as directory", docsDir?.isDirectory == true)

        // Files in directory
        val readmeFile = entries.find { it.path == "docs/readme.txt" }
        val guideFile = entries.find { it.path == "docs/guide.txt" }
        assertTrue("Should have readme.txt", readmeFile != null)
        assertTrue("Should have guide.txt", guideFile != null)

        inspector.close()
    }

    @Test
    fun `selecting nested directory should include all nested content`() = runTest {
        // Arrange - Create ZIP with nested directories
        val zipFile = createTestZip(mapOf(
            "root/" to "",
            "root/level1/" to "",
            "root/level1/level2/" to "",
            "root/level1/level2/deep.txt" to "deep content",
            "root/level1/mid.txt" to "mid content",
            "root/top.txt" to "top content"
        ))

        val inspector = ZipInspector(zipFile)

        // Act - Get all entries under root/
        val entries = inspector.entries().toList()
        val rootEntries = entries.filter { it.path.startsWith("root/") }

        // Assert - All nested content is included
        assertEquals("Should have 6 entries under root/", 6, rootEntries.size)

        // Verify all paths
        val paths = rootEntries.map { it.path }.toSet()
        assertTrue("Should contain root/", paths.contains("root/"))
        assertTrue("Should contain root/level1/", paths.contains("root/level1/"))
        assertTrue("Should contain root/level1/level2/", paths.contains("root/level1/level2/"))
        assertTrue("Should contain root/level1/level2/deep.txt", paths.contains("root/level1/level2/deep.txt"))
        assertTrue("Should contain root/level1/mid.txt", paths.contains("root/level1/mid.txt"))
        assertTrue("Should contain root/top.txt", paths.contains("root/top.txt"))

        inspector.close()
    }

    @Test
    fun `directory with mixed files and subdirectories should include all`() = runTest {
        // Arrange - Create ZIP with mixed content
        val zipFile = createTestZip(mapOf(
            "project/" to "",
            "project/README.md" to "readme",
            "project/src/" to "",
            "project/src/main.kt" to "code",
            "project/src/utils.kt" to "utils",
            "project/tests/" to "",
            "project/tests/test1.kt" to "test1",
            "project/build.gradle" to "gradle"
        ))

        val inspector = ZipInspector(zipFile)

        // Act - Get all entries under project/
        val entries = inspector.entries().toList()
        val projectEntries = entries.filter { it.path.startsWith("project/") }

        // Assert - All files and directories included
        assertEquals("Should have 8 entries under project/", 8, projectEntries.size)

        // Verify directories are marked correctly
        val directories = projectEntries.filter { it.isDirectory }
        assertEquals("Should have 3 directories", 3, directories.size)

        // Verify files are marked correctly
        val files = projectEntries.filter { !it.isDirectory }
        assertEquals("Should have 5 files", 5, files.size)

        inspector.close()
    }

    @Test
    fun `empty directory should be included in selection`() = runTest {
        // Arrange - Create ZIP with empty directory
        val zipFile = createTestZip(mapOf(
            "empty/" to "",
            "nonempty/" to "",
            "nonempty/file.txt" to "content"
        ))

        val inspector = ZipInspector(zipFile)

        // Act - Get all entries
        val entries = inspector.entries().toList()

        // Assert - Empty directory is present
        val emptyDir = entries.find { it.path == "empty/" }
        assertTrue("Should have empty/ directory", emptyDir != null)
        assertTrue("Empty directory should be marked as directory", emptyDir?.isDirectory == true)

        // Verify no files under empty/
        val emptyDirFiles = entries.filter { it.path.startsWith("empty/") && it.path != "empty/" }
        assertTrue("Empty directory should have no files", emptyDirFiles.isEmpty())

        inspector.close()
    }

    // ========== Task #74: Unicode Path Tests ==========

    @Test
    fun `should handle Unicode characters in file names`() = runTest {
        // Arrange - Create ZIP with Unicode file names
        val zipFile = createTestZip(mapOf(
            "café.txt" to "café content",
            "文档.txt" to "chinese content",
            "файл.txt" to "russian content",
            "αρχείο.txt" to "greek content"
        ))

        val inspector = ZipInspector(zipFile)

        // Act - Get all entries
        val entries = inspector.entries().toList()

        // Assert - All Unicode names preserved
        assertEquals("Should have 4 entries", 4, entries.size)

        val paths = entries.map { it.path }.toSet()
        assertTrue("Should contain café.txt", paths.contains("café.txt"))
        assertTrue("Should contain 文档.txt", paths.contains("文档.txt"))
        assertTrue("Should contain файл.txt", paths.contains("файл.txt"))
        assertTrue("Should contain αρχείο.txt", paths.contains("αρχείο.txt"))

        inspector.close()
    }

    @Test
    fun `should handle Unicode characters in directory names`() = runTest {
        // Arrange - Create ZIP with Unicode directory names
        val zipFile = createTestZip(mapOf(
            "文档/" to "",
            "文档/readme.txt" to "content",
            "Документы/" to "",
            "Документы/файл.txt" to "content",
            "ελληνικά/" to "",
            "ελληνικά/αρχείο.txt" to "content"
        ))

        val inspector = ZipInspector(zipFile)

        // Act - Get all entries
        val entries = inspector.entries().toList()

        // Assert - All Unicode directory names preserved
        assertEquals("Should have 6 entries", 6, entries.size)

        val paths = entries.map { it.path }.toSet()
        assertTrue("Should contain 文档/", paths.contains("文档/"))
        assertTrue("Should contain 文档/readme.txt", paths.contains("文档/readme.txt"))
        assertTrue("Should contain Документы/", paths.contains("Документы/"))
        assertTrue("Should contain Документы/файл.txt", paths.contains("Документы/файл.txt"))
        assertTrue("Should contain ελληνικά/", paths.contains("ελληνικά/"))
        assertTrue("Should contain ελληνικά/αρχείο.txt", paths.contains("ελληνικά/αρχείο.txt"))

        inspector.close()
    }

    @Test
    fun `should handle emoji in file names`() = runTest {
        // Arrange - Create ZIP with emoji file names
        val zipFile = createTestZip(mapOf(
            "😀happy.txt" to "happy content",
            "📁folder/" to "",
            "📁folder/📄document.txt" to "document content",
            "🎉party🎊.txt" to "party content"
        ))

        val inspector = ZipInspector(zipFile)

        // Act - Get all entries
        val entries = inspector.entries().toList()

        // Assert - All emoji names preserved
        assertEquals("Should have 4 entries", 4, entries.size)

        val paths = entries.map { it.path }.toSet()
        assertTrue("Should contain 😀happy.txt", paths.contains("😀happy.txt"))
        assertTrue("Should contain 📁folder/", paths.contains("📁folder/"))
        assertTrue("Should contain 📁folder/📄document.txt", paths.contains("📁folder/📄document.txt"))
        assertTrue("Should contain 🎉party🎊.txt", paths.contains("🎉party🎊.txt"))

        inspector.close()
    }

    @Test
    fun `should handle special characters in file names`() = runTest {
        // Arrange - Create ZIP with special characters
        val zipFile = createTestZip(mapOf(
            "file (1).txt" to "content",
            "file [copy].txt" to "content",
            "file & data.txt" to "content",
            "file@home.txt" to "content",
            "file#1.txt" to "content"
        ))

        val inspector = ZipInspector(zipFile)

        // Act - Get all entries
        val entries = inspector.entries().toList()

        // Assert - All special characters preserved
        assertEquals("Should have 5 entries", 5, entries.size)

        val paths = entries.map { it.path }.toSet()
        assertTrue("Should contain file (1).txt", paths.contains("file (1).txt"))
        assertTrue("Should contain file [copy].txt", paths.contains("file [copy].txt"))
        assertTrue("Should contain file & data.txt", paths.contains("file & data.txt"))
        assertTrue("Should contain file@home.txt", paths.contains("file@home.txt"))
        assertTrue("Should contain file#1.txt", paths.contains("file#1.txt"))

        inspector.close()
    }

    @Test
    fun `should handle mixed Unicode and ASCII in paths`() = runTest {
        // Arrange - Create ZIP with mixed encoding
        val zipFile = createTestZip(mapOf(
            "projects/" to "",
            "projects/café-app/" to "",
            "projects/café-app/README.md" to "readme",
            "projects/café-app/src/" to "",
            "projects/café-app/src/文档.kt" to "code",
            "projects/normal-app/" to "",
            "projects/normal-app/file.txt" to "content"
        ))

        val inspector = ZipInspector(zipFile)

        // Act - Get all entries
        val entries = inspector.entries().toList()

        // Assert - All mixed paths preserved
        assertEquals("Should have 7 entries", 7, entries.size)

        val paths = entries.map { it.path }.toSet()
        assertTrue("Should contain projects/café-app/", paths.contains("projects/café-app/"))
        assertTrue("Should contain projects/café-app/src/文档.kt", paths.contains("projects/café-app/src/文档.kt"))
        assertTrue("Should contain projects/normal-app/", paths.contains("projects/normal-app/"))

        inspector.close()
    }

    @Test
    fun `should handle whitespace in file names`() = runTest {
        // Arrange - Create ZIP with various whitespace
        val zipFile = createTestZip(mapOf(
            "file with spaces.txt" to "content",
            "file\twith\ttabs.txt" to "content",
            "  leading spaces.txt" to "content",
            "trailing spaces  .txt" to "content"
        ))

        val inspector = ZipInspector(zipFile)

        // Act - Get all entries
        val entries = inspector.entries().toList()

        // Assert - All whitespace preserved
        assertEquals("Should have 4 entries", 4, entries.size)

        val paths = entries.map { it.path }
        assertTrue("Should contain file with spaces", paths.any { it.contains("file with spaces") })
        assertTrue("Should contain tabs", paths.any { it.contains("tabs") })
        assertTrue("Should contain leading spaces", paths.any { it.contains("leading spaces") })
        assertTrue("Should contain trailing spaces", paths.any { it.contains("trailing spaces") })

        inspector.close()
    }

    // ========== Helper Functions ==========

    private fun createTestZip(entries: Map<String, String>): File {
        val zipFile = tempFolder.newFile("test.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            entries.forEach { (path, content) ->
                val entry = ZipEntry(path)
                zos.putNextEntry(entry)
                if (content.isNotEmpty()) {
                    zos.write(content.toByteArray())
                }
                zos.closeEntry()
            }
        }
        return zipFile
    }
}
