package app.otter.domain.usecase

import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ResourcePath
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit tests for selective extraction logic.
 *
 * Verifies that:
 * 1. Select All extracts ALL files
 * 2. Partial selection extracts ONLY selected files
 * 3. Hierarchical selection preserves folder structure
 */
class ExtractSelectedItemsUseCaseTest {

    // Test data - Archive with 1000 files and 6 folders
    private val archivePath = "/storage/emulated/0/Download/test_archive.zip"
    private val outputDir = File("/tmp/extracted")

    /**
     * Creates a test archive structure with files and folders.
     *
     * Structure:
     * - 1000 files at root (file1 to file1000)
     * - 6 folders (folder1 to folder6)
     * - Each folder has 10 files (file1 to file10)
     */
    private fun createTestArchiveStructure(): List<BrowsableItem> {
        val items = mutableListOf<BrowsableItem>()

        // Add 1000 files at root
        for (i in 1..1000) {
            items.add(
                BrowsableItem.ArchiveFileEntry(
                    path = ResourcePath.ArchiveEntry(archivePath, "file$i.txt"),
                    name = "file$i.txt",
                    sizeBytes = 100L,
                    lastModified = 0L,
                    archivePath = ResourcePath.ArchiveEntry(archivePath, "file$i.txt"),
                    mimeType = "text/plain"
                )
            )
        }

        // Add 6 folders with 10 files each
        for (folderNum in 1..6) {
            val folderPath = "folder$folderNum"
            items.add(
                BrowsableItem.ArchiveDirectory(
                    path = ResourcePath.ArchiveEntry(archivePath, folderPath),
                    name = "folder$folderNum",
                    sizeBytes = 0L,
                    lastModified = 0L,
                    archivePath = ResourcePath.ArchiveEntry(archivePath, folderPath)
                )
            )

            for (fileNum in 1..10) {
                items.add(
                    BrowsableItem.ArchiveFileEntry(
                        path = ResourcePath.ArchiveEntry(archivePath, "$folderPath/file$fileNum.txt"),
                        name = "file$fileNum.txt",
                        sizeBytes = 100L,
                        lastModified = 0L,
                        archivePath = ResourcePath.ArchiveEntry(archivePath, "$folderPath/file$fileNum.txt"),
                        mimeType = "text/plain"
                    )
                )
            }
        }

        return items
    }

    @Test
    fun `extract with Select All extracts all files`() {
        // Arrange
        val allItems = createTestArchiveStructure()
        val selectedPaths = allItems.map { it.path }.toSet()

        // Act
        val itemsToExtract = filterItemsToExtract(allItems, selectedPaths)

        // Assert
        // Should extract exactly what was selected (no duplicates)
        // Structure: 1000 root files + 6 folders + 60 files in folders = 1066 items
        assertEquals(1066, itemsToExtract.size)
        assertEquals(allItems.size, itemsToExtract.size)
        assertTrue(itemsToExtract.any { it.name == "file1.txt" })
        assertTrue(itemsToExtract.any { it.name == "file1000.txt" })
        assertTrue(itemsToExtract.any { it.name == "folder1" })
    }

    @Test
    fun `extract partial selection extracts only selected files`() {
        // Arrange
        val allItems = createTestArchiveStructure()

        // Select file1, file2, file4, file1000 (4 files)
        val selectedPaths = setOf(
            ResourcePath.ArchiveEntry(archivePath, "file1.txt"),
            ResourcePath.ArchiveEntry(archivePath, "file2.txt"),
            ResourcePath.ArchiveEntry(archivePath, "file4.txt"),
            ResourcePath.ArchiveEntry(archivePath, "file1000.txt")
        )

        // Act
        val itemsToExtract = filterItemsToExtract(allItems, selectedPaths)

        // Assert
        assertEquals(4, itemsToExtract.size)
        assertTrue(itemsToExtract.any { (it.path as ResourcePath.ArchiveEntry).entryPath == "file1.txt" })
        assertTrue(itemsToExtract.any { (it.path as ResourcePath.ArchiveEntry).entryPath == "file2.txt" })
        assertTrue(itemsToExtract.any { (it.path as ResourcePath.ArchiveEntry).entryPath == "file4.txt" })
        assertTrue(itemsToExtract.any { (it.path as ResourcePath.ArchiveEntry).entryPath == "file1000.txt" })

        assertFalse(itemsToExtract.any { (it.path as ResourcePath.ArchiveEntry).entryPath == "file3.txt" })
    }

    @Test
    fun `extract hierarchical selection preserves folder structure`() {
        // Arrange
        val allItems = createTestArchiveStructure()

        // Select:
        // - Root: file1, file2, file4, file1000
        // - folder1/file2
        // - folder3/file5, folder3/file7
        val selectedPaths = setOf(
            ResourcePath.ArchiveEntry(archivePath, "file1.txt"),
            ResourcePath.ArchiveEntry(archivePath, "file2.txt"),
            ResourcePath.ArchiveEntry(archivePath, "file4.txt"),
            ResourcePath.ArchiveEntry(archivePath, "file1000.txt"),
            ResourcePath.ArchiveEntry(archivePath, "folder1/file2.txt"),
            ResourcePath.ArchiveEntry(archivePath, "folder3/file5.txt"),
            ResourcePath.ArchiveEntry(archivePath, "folder3/file7.txt")
        )

        // Act
        val itemsToExtract = filterItemsToExtract(allItems, selectedPaths)
        val extractedPaths = itemsToExtract.map { (it.path as ResourcePath.ArchiveEntry).entryPath }

        // Assert - Root files
        assertTrue(extractedPaths.contains("file1.txt"))
        assertTrue(extractedPaths.contains("file2.txt"))
        assertTrue(extractedPaths.contains("file4.txt"))
        assertTrue(extractedPaths.contains("file1000.txt"))
        assertFalse(extractedPaths.contains("file3.txt"))

        // Assert - Folder structure preserved
        assertTrue(extractedPaths.contains("folder1"))
        assertTrue(extractedPaths.contains("folder1/file2.txt"))
        assertFalse(extractedPaths.contains("folder1/file1.txt"))

        assertTrue(extractedPaths.contains("folder3"))
        assertTrue(extractedPaths.contains("folder3/file5.txt"))
        assertTrue(extractedPaths.contains("folder3/file7.txt"))
        assertFalse(extractedPaths.contains("folder3/file1.txt"))

        // Assert - Unselected folders not included
        assertFalse(extractedPaths.contains("folder2"))
    }

    @Test
    fun `extract with no selection returns empty list`() {
        // Arrange
        val allItems = createTestArchiveStructure()
        val selectedPaths = emptySet<ResourcePath>()

        // Act
        val itemsToExtract = filterItemsToExtract(allItems, selectedPaths)

        // Assert
        assertTrue(itemsToExtract.isEmpty())
    }

    @Test
    fun `extract with folder selection includes all files in folder`() {
        // Arrange
        val allItems = createTestArchiveStructure()

        // Select folder1 (should include all 10 files inside)
        val folder1 = allItems.first { it.name == "folder1" }
        val selectedPaths = setOf(folder1.path)

        // Act
        val itemsToExtract = filterItemsToExtract(allItems, selectedPaths)
        val extractedPaths = itemsToExtract.map { (it.path as ResourcePath.ArchiveEntry).entryPath }

        // Assert - Folder + all 10 files
        assertEquals(11, itemsToExtract.size)
        assertTrue(extractedPaths.contains("folder1"))
        assertTrue(extractedPaths.contains("folder1/file1.txt"))
        assertTrue(extractedPaths.contains("folder1/file10.txt"))
    }

    @Test
    fun `extract adds parent folders automatically for nested files`() {
        // Arrange
        val allItems = createTestArchiveStructure()

        // Select ONLY folder1/file2.txt (not the folder itself)
        val selectedPaths = setOf(
            ResourcePath.ArchiveEntry(archivePath, "folder1/file2.txt")
        )

        // Act
        val itemsToExtract = filterItemsToExtract(allItems, selectedPaths)
        val extractedPaths = itemsToExtract.map { (it.path as ResourcePath.ArchiveEntry).entryPath }

        // Assert - Parent folder should be included automatically
        assertTrue(extractedPaths.contains("folder1"))
        assertTrue(extractedPaths.contains("folder1/file2.txt"))
        assertEquals(2, itemsToExtract.size) // folder1 + file2.txt
    }

    /**
     * Helper function to filter items that should be extracted based on selection.
     *
     * This is the business logic we're testing (to be implemented in UseCase).
     *
     * Rules:
     * 1. If a file is selected, extract it
     * 2. If a folder is selected, extract folder + all files inside
     * 3. If a nested file is selected, extract file + all parent folders
     * 4. Don't extract items not in selection
     */
    private fun filterItemsToExtract(
        allItems: List<BrowsableItem>,
        selectedPaths: Set<ResourcePath>
    ): List<BrowsableItem> {
        if (selectedPaths.isEmpty()) return emptyList()

        val itemsToExtract = mutableListOf<BrowsableItem>()
        val processedPaths = mutableSetOf<String>()

        // Process each selected path
        selectedPaths.forEach { selectedPath ->
            if (selectedPath !is ResourcePath.ArchiveEntry) return@forEach

            // Find the selected item
            val selectedItem = allItems.firstOrNull { it.path == selectedPath } ?: return@forEach

            when (selectedItem) {
                is BrowsableItem.ArchiveDirectory -> {
                    // Include folder + all files inside
                    if (!processedPaths.contains(selectedPath.entryPath)) {
                        itemsToExtract.add(selectedItem)
                        processedPaths.add(selectedPath.entryPath)
                    }

                    // Include all files in this folder
                    allItems.forEach { item ->
                        val itemPath = item.path
                        if (itemPath is ResourcePath.ArchiveEntry &&
                            itemPath.entryPath.startsWith("${selectedPath.entryPath}/") &&
                            !processedPaths.contains(itemPath.entryPath)
                        ) {
                            itemsToExtract.add(item)
                            processedPaths.add(itemPath.entryPath)
                        }
                    }
                }
                is BrowsableItem.ArchiveFileEntry, is BrowsableItem.ArchiveFile -> {
                    // Add parent folders first
                    val parentPaths = getParentPaths(selectedPath.entryPath)
                    parentPaths.forEach { parentPath ->
                        if (!processedPaths.contains(parentPath)) {
                            val parentItem = allItems.firstOrNull {
                                (it.path as? ResourcePath.ArchiveEntry)?.entryPath == parentPath
                            }
                            if (parentItem != null) {
                                itemsToExtract.add(parentItem)
                                processedPaths.add(parentPath)
                            }
                        }
                    }

                    // Add the file itself
                    if (!processedPaths.contains(selectedPath.entryPath)) {
                        itemsToExtract.add(selectedItem)
                        processedPaths.add(selectedPath.entryPath)
                    }
                }
                else -> {
                    // FileSystemFile, FileSystemDirectory - add as-is
                    if (!processedPaths.contains(selectedPath.toString())) {
                        itemsToExtract.add(selectedItem)
                        processedPaths.add(selectedPath.toString())
                    }
                }
            }
        }

        return itemsToExtract
    }

    /**
     * Returns all parent folder paths for a given path.
     *
     * Example: "folder1/folder2/file.txt" → ["folder1", "folder1/folder2"]
     */
    private fun getParentPaths(path: String): List<String> {
        val parts = path.split("/")
        if (parts.size <= 1) return emptyList()

        val parents = mutableListOf<String>()
        for (i in 1 until parts.size) {
            parents.add(parts.subList(0, i).joinToString("/"))
        }
        return parents
    }
}
