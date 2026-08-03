package app.otter.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.ExtractionOptions
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.ArchiveFile
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.domain.model.ResourcePath
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class ArchiveRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var zipExtractor: ArchiveExtractor
    private lateinit var repository: ArchiveRepositoryImpl
    private val uriMockCache = mutableMapOf<String, Uri>()

    @Before
    fun setup() {
        mockkObject(ResourcePathConverter)
        every { ResourcePathConverter.toUri(any()) } answers {
            val path = firstArg<ResourcePath>()
            val uriString = when (path) {
                is ResourcePath.FileSystem -> path.path
                is ResourcePath.ArchiveEntry -> path.archivePath
            }
            getOrCreateUriMock(uriString)
        }
        every { ResourcePathConverter.toFile(any()) } answers {
            val uri = firstArg<Uri>()
            uri.path?.let { File(it) }
        }

        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        zipExtractor = mockk(relaxed = true)

        every { context.contentResolver } returns contentResolver
        every { zipExtractor.supports(ArchiveType.ZIP) } returns true

        repository = ArchiveRepositoryImpl(context, listOf(zipExtractor))
    }

    @After
    fun tearDown() {
        uriMockCache.clear()
        unmockkAll()
    }

    private fun getOrCreateUriMock(uriString: String): Uri =
        uriMockCache.getOrPut(uriString) { createUriMock(uriString) }

    private fun createUriMock(uriString: String): Uri {
        val scheme = when {
            uriString.startsWith("file://") -> "file"
            uriString.startsWith("content://") -> "content"
            else -> ""
        }
        val path = when {
            uriString.startsWith("file:///") -> uriString.removePrefix("file://")
            uriString.startsWith("file://") -> null
            uriString.startsWith("content://") -> "/${uriString.substringAfter("://").substringAfter("/")}"
            else -> uriString
        }
        return mockk<Uri>(relaxed = true).also { mock ->
            every { mock.scheme } returns scheme
            every { mock.toString() } returns uriString
            every { mock.path } returns path
            every { mock.lastPathSegment } returns uriString.substringAfterLast("/").takeIf { it.isNotBlank() }
        }
    }

    @Test
    fun `should select correct extractor for archive type`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(any()) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } returns ExtractionResult.Success("/downloads/test", 5)

        repository.extractArchive(archive, destinationPath).toList()

        verify { zipExtractor.supports(ArchiveType.ZIP) }
    }

    @Test
    fun `should return error when no extractor supports archive type`() = runTest {
        val unsupportedExtractor: ArchiveExtractor = mockk {
            every { supports(any()) } returns false
        }
        val repo = ArchiveRepositoryImpl(context, listOf(unsupportedExtractor))

        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")

        val results = repo.extractArchive(archive, destinationPath).toList()

        assertTrue(results.any { it is ExtractionProgress.Error })
        val error = results.first { it is ExtractionProgress.Error } as ExtractionProgress.Error
        assertTrue(error.message.contains("No extractor"))
    }

    @Test
    fun `should return error when cannot open archive`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")

        every { contentResolver.openInputStream(any()) } returns null

        val results = repository.extractArchive(archive, destinationPath).toList()

        assertTrue(results.any { it is ExtractionProgress.Error })
        val error = results.first { it is ExtractionProgress.Error } as ExtractionProgress.Error
        assertTrue(error.message.contains("Cannot open archive"))
    }

    @Test
    fun `should propagate extraction success`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(any()) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } returns ExtractionResult.Success("/downloads/test", 5)

        val results = repository.extractArchive(archive, destinationPath).toList()

        assertTrue(results.any { it is ExtractionProgress.Success })
        val success = results.first { it is ExtractionProgress.Success } as ExtractionProgress.Success
        assertEquals("/downloads/test", success.outputPath)
        assertEquals(5, success.extractedCount)
    }

    @Test
    fun `should propagate extraction failure`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(any()) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } returns ExtractionResult.Failure("Corrupted archive", null)

        val results = repository.extractArchive(archive, destinationPath).toList()

        assertTrue(results.any { it is ExtractionProgress.Error })
        val error = results.first { it is ExtractionProgress.Error } as ExtractionProgress.Error
        assertTrue(error.message.contains("Corrupted archive"))
    }

    @Test
    fun `should use destination path directly`() = runTest {
        val tempDir = temporaryFolder.newFolder("test")
        val destinationPath = ResourcePath.FileSystem(tempDir.absolutePath)
        val archive = createTestArchive()
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(any()) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } answers {
            val destFile = secondArg<File>()
            assertEquals(tempDir.absolutePath, destFile.absolutePath)
            ExtractionResult.Success(destFile.absolutePath, 3)
        }

        repository.extractArchive(archive, destinationPath).toList()
    }

    @Test
    fun `should emit progress events during extraction`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(any()) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } answers {
            val onProgress = arg<(ExtractionProgress) -> Unit>(5)
            onProgress(ExtractionProgress.Extracting("file1.txt", 1, 2, 0.5f))
            onProgress(ExtractionProgress.Extracting("file2.txt", 2, 2, 1.0f))
            ExtractionResult.Success("/downloads/test", 2)
        }

        val results = repository.extractArchive(archive, destinationPath).toList()

        assertTrue("Should have at least 4 events, got ${results.size}: ${results.map { it::class.simpleName }}", results.size >= 4)
        assertTrue("First event should be Idle", results[0] is ExtractionProgress.Idle)
        assertTrue("Should contain Extracting events", results.any { it is ExtractionProgress.Extracting })
        assertTrue("Last event should be Success", results.any { it is ExtractionProgress.Success })
    }

    @Test
    fun `should handle cancellation gracefully`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(any()) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } throws kotlinx.coroutines.CancellationException("Extraction cancelled")

        val results = repository.extractArchive(archive, destinationPath).toList()

        assertTrue("Should emit Idle before cancellation", results.isNotEmpty())
        assertTrue("First event should be Idle", results[0] is ExtractionProgress.Idle)
    }

    // ========== selectedItems propagation ==========

    @Test
    fun `should pass selectedItems to extractor when provided`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())
        val selectedItems = listOf("folder/file1.txt", "folder/file2.jpg")

        every { contentResolver.openInputStream(any()) } returns inputStream
        var capturedSelectedItems: List<String>? = null
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } answers {
            capturedSelectedItems = (arg(4) as ExtractionOptions).selectedItems
            ExtractionResult.Success("/downloads/test", 2)
        }

        repository.extractArchive(archive, destinationPath, selectedItems).toList()

        assertEquals("selectedItems must be passed to extractor", selectedItems, capturedSelectedItems)
    }

    @Test
    fun `should pass null selectedItems to extractor when not provided`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(any()) } returns inputStream
        var capturedSelectedItems: List<String>? = listOf("placeholder")
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } answers {
            capturedSelectedItems = (arg(4) as ExtractionOptions).selectedItems
            ExtractionResult.Success("/downloads/test", 5)
        }

        repository.extractArchive(archive, destinationPath, null).toList()

        assertNull("null selectedItems must be passed through to extractor", capturedSelectedItems)
    }

    @Test
    fun `should pass empty selectedItems list to extractor`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(any()) } returns inputStream
        var capturedSelectedItems: List<String>? = null
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } answers {
            capturedSelectedItems = (arg(4) as ExtractionOptions).selectedItems
            ExtractionResult.Success("/downloads/test", 0)
        }

        repository.extractArchive(archive, destinationPath, emptyList()).toList()

        assertNotNull("Empty list must be passed (not null)", capturedSelectedItems)
        assertTrue("Empty list must be empty", capturedSelectedItems!!.isEmpty())
    }

    // ========== sourceFile propagation ==========

    @Test
    fun `should pass non-null sourceFile when archive uri resolves to existing file`() = runTest {
        val realFile = temporaryFolder.newFile("real_archive.zip")
        val archive = ArchiveFile(
            path = ResourcePath.FileSystem(realFile.absolutePath),
            name = realFile.name,
            sizeBytes = realFile.length(),
            mimeType = "application/zip",
            type = ArchiveType.ZIP
        )
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream(byteArrayOf())

        var capturedSourceFile: File? = null
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } answers {
            capturedSourceFile = (arg(4) as ExtractionOptions).sourceFile
            ExtractionResult.Success("/downloads/test", 1)
        }

        repository.extractArchive(archive, destinationPath).toList()

        assertNotNull("sourceFile must be non-null when archive file exists on disk", capturedSourceFile)
        assertEquals(realFile.absolutePath, capturedSourceFile!!.absolutePath)
    }

    @Test
    fun `should pass null sourceFile when archive uri path does not exist on disk`() = runTest {
        val archive = createTestArchive() // file:///test.zip — does not exist on FS
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream(byteArrayOf())

        var capturedSourceFile: File? = File("sentinel")
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } answers {
            capturedSourceFile = (arg(4) as ExtractionOptions).sourceFile
            ExtractionResult.Success("/downloads/test", 1)
        }

        repository.extractArchive(archive, destinationPath).toList()

        assertNull("sourceFile must be null when file does not exist on disk", capturedSourceFile)
    }

    // ========== null destination path ==========

    @Test
    fun `should emit Error when destination path is null`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("content://test/destination")

        val mockDestUri = mockk<Uri>()
        every { mockDestUri.path } returns null
        every { ResourcePathConverter.toUri(destinationPath) } returns mockDestUri

        val results = repository.extractArchive(archive, destinationPath).toList()

        assertTrue(
            "Should emit Error for null destination path",
            results.any { it is ExtractionProgress.Error }
        )
        val error = results.filterIsInstance<ExtractionProgress.Error>().first()
        assertTrue(
            "Error message should mention destination path",
            error.message.contains("destination") || error.message.contains("Invalid")
        )
    }

    // ========== DISPLAY_NAME cursor ==========

    @Test
    fun `should use DISPLAY_NAME from cursor as source file name when query succeeds`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { mockCursor.getString(0) } returns "my_custom_name.zip"

        every { contentResolver.query(any(), any(), null, null, null) } returns mockCursor
        every { contentResolver.openInputStream(any()) } returns inputStream

        var capturedSourceFileName: String? = null
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } answers {
            capturedSourceFileName = arg(3)
            ExtractionResult.Success("/downloads/test", 1)
        }

        repository.extractArchive(archive, destinationPath).toList()

        assertEquals(
            "sourceFileName must be the DISPLAY_NAME from cursor",
            "my_custom_name.zip",
            capturedSourceFileName
        )
    }

    @Test
    fun `when query returns null getFileNameFromUri uses last path segment`() = runTest {
        val archive = createTestArchive()
        val destinationPath = ResourcePath.FileSystem("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.query(any(), any(), null, null, null) } returns null
        every { contentResolver.openInputStream(any()) } returns inputStream

        var capturedSourceFileName: String? = null
        coEvery {
            zipExtractor.extract(any(), any(), any(), any(), any(), any())
        } answers {
            capturedSourceFileName = arg(3)
            ExtractionResult.Success("/downloads/test", 1)
        }

        repository.extractArchive(archive, destinationPath).toList()

        assertEquals(
            "sourceFileName must fall back to last path segment when query returns null",
            "test.zip",
            capturedSourceFileName
        )
    }

    private fun createTestArchive() = ArchiveFile(
        path = ResourcePath.FileSystem("file:///test.zip"),
        name = "test.zip",
        sizeBytes = 1024L,
        mimeType = "application/zip",
        type = ArchiveType.ZIP
    )
}
