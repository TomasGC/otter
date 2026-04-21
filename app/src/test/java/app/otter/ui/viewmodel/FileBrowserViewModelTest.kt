package app.otter.ui.viewmodel

import android.net.Uri
import app.otter.domain.model.FileItem
import app.otter.domain.usecase.BrowseFilesUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileBrowserViewModelTest {

    private lateinit var browseFilesUseCase: BrowseFilesUseCase
    private lateinit var viewModel: FileBrowserViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        browseFilesUseCase = mockk()

        // Mock default start directory
        val mockFiles = listOf(
            createFileItem("folder1", isDirectory = true),
            createFileItem("archive.zip", isArchive = true),
            createFileItem("file.txt", isDirectory = false)
        )
        coEvery { browseFilesUseCase(any()) } returns Result.success(mockFiles)

        viewModel = FileBrowserViewModel(browseFilesUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Success with sorted files`() {
        // When
        val state = viewModel.uiState.value

        // Then
        assertTrue(state is FileBrowserUiState.Success)
        val successState = state as FileBrowserUiState.Success
        assertEquals(3, successState.files.size)

        // Check sorting: archives first, then directories, then files
        assertEquals("archive.zip", successState.files[0].name)
        assertEquals("folder1", successState.files[1].name)
        assertEquals("file.txt", successState.files[2].name)
    }

    @Test
    fun `toggleArchiveFilter filters to archives only`() {
        // Given
        viewModel.uiState.value as FileBrowserUiState.Success

        // When
        viewModel.toggleArchiveFilter()

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertTrue(state.filterArchivesOnly)
        assertEquals(1, state.files.size)
        assertTrue(state.files.all { it.isArchive })
    }

    @Test
    fun `toggleArchiveFilter twice returns to all files`() {
        // When
        viewModel.toggleArchiveFilter()
        viewModel.toggleArchiveFilter()

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertFalse(state.filterArchivesOnly)
        assertEquals(3, state.files.size)
    }

    @Test
    fun `setSortOrder NAME_ASC sorts alphabetically`() {
        // When
        viewModel.setSortOrder(SortOrder.NAME_ASC)

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals("archive.zip", state.files[0].name)
        assertEquals("file.txt", state.files[1].name)
        assertEquals("folder1", state.files[2].name)
    }

    @Test
    fun `setSortOrder NAME_DESC sorts reverse alphabetically`() {
        // When
        viewModel.setSortOrder(SortOrder.NAME_DESC)

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals("folder1", state.files[0].name)
        assertEquals("file.txt", state.files[1].name)
        assertEquals("archive.zip", state.files[2].name)
    }

    @Test
    fun `enterSelectionMode sets isSelectionMode to true`() {
        // When
        viewModel.enterSelectionMode()

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertTrue(state.isSelectionMode)
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `toggleFileSelection adds and removes files`() {
        // Given
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        val file = state.files.first()

        viewModel.enterSelectionMode()

        // When - Select file
        viewModel.toggleFileSelection(file)

        // Then
        var currentState = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(1, currentState.selectedCount)
        assertTrue(viewModel.isFileSelected(file))

        // When - Deselect file
        viewModel.toggleFileSelection(file)

        // Then
        currentState = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(0, currentState.selectedCount)
        assertFalse(viewModel.isFileSelected(file))
    }

    @Test
    fun `exitSelectionMode clears selection`() {
        // Given
        viewModel.enterSelectionMode()
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        val file = state.files.first()
        viewModel.toggleFileSelection(file)

        // When
        viewModel.exitSelectionMode()

        // Then
        val newState = viewModel.uiState.value as FileBrowserUiState.Success
        assertFalse(newState.isSelectionMode)
        assertEquals(0, newState.selectedCount)
    }

    @Test
    fun `getSelectedFiles returns only selected files`() {
        // Given
        viewModel.enterSelectionMode()
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        val file1 = state.files[0]
        val file2 = state.files[1]

        // When
        viewModel.toggleFileSelection(file1)
        viewModel.toggleFileSelection(file2)

        // Then
        val selected = viewModel.getSelectedFiles()
        assertEquals(2, selected.size)
        assertTrue(selected.contains(file1))
        assertTrue(selected.contains(file2))
    }

    @Test
    fun `updateExtractionProgress changes state to Extracting`() {
        // When
        viewModel.updateExtractionProgress(
            fileName = "test.zip",
            currentFile = "file1.txt",
            extractedCount = 10,
            totalCount = 100,
            progress = 0.1f
        )

        // Then
        val state = viewModel.uiState.value
        assertTrue(state is FileBrowserUiState.Extracting)
        val extractingState = state as FileBrowserUiState.Extracting
        assertEquals("test.zip", extractingState.fileName)
        assertEquals("file1.txt", extractingState.currentFile)
        assertEquals(10, extractingState.extractedCount)
        assertEquals(100, extractingState.totalCount)
        assertEquals(0.1f, extractingState.progress, 0.001f)
    }

    @Test
    fun `moveExtractionToBackground returns to previous Success state`() {
        // Given - Get initial success state
        val initialState = viewModel.uiState.value as FileBrowserUiState.Success

        // When - Go to extracting
        viewModel.updateExtractionProgress("test.zip", "file.txt", 1, 10, 0.1f)
        assertTrue(viewModel.uiState.value is FileBrowserUiState.Extracting)

        // Then - Move to background
        viewModel.moveExtractionToBackground()
        val newState = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(initialState.files.size, newState.files.size)
    }

    @Test
    fun `canNavigateUp returns false at root level`() {
        // Then
        assertFalse(viewModel.canNavigateUp())
    }

    @Test
    fun `canNavigateUp returns true after navigating into directory`() {
        // Given
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        val folder = state.files.find { it.isDirectory }!!

        // Mock navigation result
        coEvery { browseFilesUseCase(folder.uri) } returns Result.success(emptyList())

        // When
        viewModel.navigateInto(folder)

        // Then
        assertTrue(viewModel.canNavigateUp())
    }

    // Helper functions
    private fun createFileItem(
        name: String,
        isDirectory: Boolean = false,
        isArchive: Boolean = false
    ): FileItem {
        return FileItem(
            uri = Uri.parse("file:///$name"),
            name = name,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) null else 1024L,
            lastModified = System.currentTimeMillis(),
            mimeType = when {
                isArchive -> "application/zip"
                isDirectory -> null
                else -> "text/plain"
            }
        )
    }
}
