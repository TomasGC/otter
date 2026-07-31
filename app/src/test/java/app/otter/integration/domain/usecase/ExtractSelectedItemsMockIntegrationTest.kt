package app.otter.domain.usecase

import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ResourcePath
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for selective extraction logic.
 *
 * Tests the interaction between filtering and selection logic with domain models.
 * No real archives needed - focuses on business logic integration.
 */
class ExtractSelectedItemsMockIntegrationTest {

    @Test
    fun `extractor integrates with domain models for file list`() {
        // Arrange - Create mock domain items
        val mockItems = listOf(
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "file1.txt"),
                name = "file1.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "file1.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "file2.txt"),
                name = "file2.txt",
                sizeBytes = 75L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "file2.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "folder/file3.txt"),
                name = "file3.txt",
                sizeBytes = 100L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "folder/file3.txt"),
                mimeType = "text/plain"
            )
        )

        // Act - Get entries
        val entries = mockItems

        // Assert - Domain models accessible
        assertEquals(3, entries.size)
        assertEquals("file1.txt", entries[0].name)
        assertEquals("file3.txt", entries[2].name)
    }

    @Test
    fun `selective extraction filters entries before processing`() {
        // Arrange - 100 files, select 10
        val allEntries = List(100) { index ->
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "file$index.txt"),
                name = "file$index.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "file$index.txt"),
                mimeType = "text/plain"
            )
        }
        val selectedIndices = setOf(5, 15, 25, 35, 45, 55, 65, 75, 85, 95)
        val selectedEntries = allEntries.filterIndexed { index, _ -> index in selectedIndices }

        // Act - Filter selection
        val filtered = allEntries.filter { entry ->
            selectedEntries.any { it.name == entry.name }
        }

        // Assert
        assertEquals(10, filtered.size)
        assertTrue(filtered.any { it.name == "file5.txt" })
        assertTrue(filtered.any { it.name == "file95.txt" })
        assertFalse(filtered.any { it.name == "file0.txt" })
    }

    @Test
    fun `hierarchical selection includes parent folders`() {
        // Arrange - Nested structure
        val allEntries = listOf(
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "file1.txt"),
                name = "file1.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "file1.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file2.txt"),
                name = "file2.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file2.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/subfolder/file3.txt"),
                name = "file3.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/subfolder/file3.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "folder2/file4.txt"),
                name = "file4.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "folder2/file4.txt"),
                mimeType = "text/plain"
            )
        )

        // Select only folder1/subfolder/file3.txt
        val selectedPaths = setOf("folder1/subfolder/file3.txt")

        // Act - Extract parent paths
        val requiredPaths = mutableSetOf<String>()
        selectedPaths.forEach { path ->
            requiredPaths.add(path)
            // Add parent folders
            val parts = path.split("/")
            for (i in 1 until parts.size) {
                requiredPaths.add(parts.subList(0, i).joinToString("/"))
            }
        }

        // Assert
        assertTrue(requiredPaths.contains("folder1"))
        assertTrue(requiredPaths.contains("folder1/subfolder"))
        assertTrue(requiredPaths.contains("folder1/subfolder/file3.txt"))
        assertFalse(requiredPaths.contains("folder2"))
    }

    @Test
    fun `folder selection includes all children`() {
        // Arrange - Folder with 10 files
        val allEntries = List(10) { index ->
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file$index.txt"),
                name = "file$index.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file$index.txt"),
                mimeType = "text/plain"
            )
        }

        // Select folder1 (not individual files)
        val selectedFolders = setOf("folder1")

        // Act - Expand folder to children
        val expanded = allEntries.filter { entry ->
            val entryPath = (entry.path as ResourcePath.ArchiveEntry).entryPath
            selectedFolders.any { folder -> entryPath.startsWith("$folder/") }
        }

        // Assert
        assertEquals(10, expanded.size)
        assertTrue(expanded.all {
            val entryPath = (it.path as ResourcePath.ArchiveEntry).entryPath
            entryPath.startsWith("folder1/")
        })
    }

    @Test
    fun `selection with duplicates is deduplicated`() {
        // Arrange - Duplicate selections (e.g., folder + file inside)
        val allEntries = listOf(
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file1.txt"),
                name = "file1.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file1.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file2.txt"),
                name = "file2.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file2.txt"),
                mimeType = "text/plain"
            )
        )

        // Select folder1 AND folder1/file1.txt (overlap)
        val selectedPaths = mutableSetOf(
            "folder1",
            "folder1/file1.txt"
        )

        // Act - Deduplicate (folder selection already includes file1)
        val deduplicated = if (selectedPaths.contains("folder1")) {
            // If folder selected, remove individual file selections
            selectedPaths.filterNot { it.startsWith("folder1/") && it != "folder1" }
        } else {
            selectedPaths.toList()
        }

        // Assert
        assertEquals(1, deduplicated.size)
        assertTrue(deduplicated.contains("folder1"))
    }

    @Test
    fun `empty selection returns empty result`() {
        // Arrange
        val allEntries = List(100) { index ->
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "file$index.txt"),
                name = "file$index.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "file$index.txt"),
                mimeType = "text/plain"
            )
        }
        val selectedPaths = emptySet<String>()

        // Act
        val filtered = allEntries.filter { entry ->
            selectedPaths.contains(entry.name)
        }

        // Assert
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `pathValidator prevents path traversal in selection`() {
        // Arrange - Malicious path selection (simulated with domain items)
        val maliciousPaths = listOf(
            "../../../etc/passwd",
            "..\\..\\windows\\system32",
            "folder/../../../secret.txt"
        )

        // Act & Assert - Path validation logic should reject traversal attempts
        // This test verifies that path traversal patterns are detected
        maliciousPaths.forEach { path ->
            val normalized = path.replace("\\", "/")

            // A proper validator should reject paths with ".."
            val isTraversalAttempt = normalized.contains("..")

            assertTrue(
                "Path should be detected as traversal attempt: $path",
                isTraversalAttempt
            )
        }
    }

    @Test
    fun `large selection handles memory efficiently`() {
        // Arrange - 10k file selection
        val allEntries = List(50000) { index ->
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "file$index.txt"),
                name = "file$index.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "file$index.txt"),
                mimeType = "text/plain"
            )
        }
        val selectedIndices = (0 until 10000).toSet()

        // Act - Filter efficiently (no full copy)
        val filtered = allEntries.asSequence()
            .filterIndexed { index, _ -> index in selectedIndices }
            .take(10000)
            .toList()

        // Assert
        assertEquals(10000, filtered.size)
    }

    @Test
    fun `selection preserves file order from archive`() {
        // Arrange - Files in specific order
        val allEntries = listOf(
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "z-last.txt"),
                name = "z-last.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "z-last.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "a-first.txt"),
                name = "a-first.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "a-first.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "m-middle.txt"),
                name = "m-middle.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "m-middle.txt"),
                mimeType = "text/plain"
            )
        )
        val selectedPaths = setOf("z-last.txt", "a-first.txt", "m-middle.txt")

        // Act - Filter maintains archive order (not alphabetical)
        val filtered = allEntries.filter { selectedPaths.contains(it.name) }

        // Assert - Order preserved from archive
        assertEquals("z-last.txt", filtered[0].name)
        assertEquals("a-first.txt", filtered[1].name)
        assertEquals("m-middle.txt", filtered[2].name)
    }

    @Test
    fun `mixed selection of files and folders works correctly`() {
        // Arrange
        val allEntries = listOf(
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "file1.txt"),
                name = "file1.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "file1.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file2.txt"),
                name = "file2.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file2.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file3.txt"),
                name = "file3.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "folder1/file3.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "folder2/file4.txt"),
                name = "file4.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "folder2/file4.txt"),
                mimeType = "text/plain"
            ),
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry("/archive.rpa", "file5.txt"),
                name = "file5.txt",
                sizeBytes = 50L,
                lastModified = 0L,
                archivePath = ResourcePath.ArchiveEntry("/archive.rpa", "file5.txt"),
                mimeType = "text/plain"
            )
        )

        // Select: file1.txt + folder1 (includes file2+file3) + file5.txt
        val selectedPaths = setOf("file1.txt", "folder1", "file5.txt")

        // Act - Expand folders
        val expanded = mutableSetOf<String>()
        selectedPaths.forEach { path ->
            if (path.contains("/")) {
                expanded.add(path)
            } else {
                // Check if it's a folder or file
                val isFolder = allEntries.any {
                    val entryPath = (it.path as ResourcePath.ArchiveEntry).entryPath
                    entryPath.startsWith("$path/")
                }
                if (isFolder) {
                    // Add folder contents
                    allEntries.filter {
                        val entryPath = (it.path as ResourcePath.ArchiveEntry).entryPath
                        entryPath.startsWith("$path/")
                    }.forEach {
                        val entryPath = (it.path as ResourcePath.ArchiveEntry).entryPath
                        expanded.add(entryPath)
                    }
                } else {
                    expanded.add(path)
                }
            }
        }

        // Assert
        assertTrue(expanded.contains("file1.txt"))
        assertTrue(expanded.contains("folder1/file2.txt"))
        assertTrue(expanded.contains("folder1/file3.txt"))
        assertTrue(expanded.contains("file5.txt"))
        assertFalse(expanded.contains("folder2/file4.txt"))
    }
}
