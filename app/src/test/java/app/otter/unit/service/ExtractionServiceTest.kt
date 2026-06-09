package app.otter.service

import android.content.Context
import android.content.Intent
import app.otter.domain.model.ResourcePath
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ExtractionService.newIntent() static factory method.
 * Tests verify correct intent construction without running the Service itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExtractionServiceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk(relaxed = true)
    }

    @Test
    fun `newIntent includes fileName extra`() {
        val intent = ExtractionService.newIntent(
            context = context,
            archiveUri = ResourcePath.FileSystem("file:///test.zip"),
            fileName = "test.zip"
        )

        assertEquals("test.zip", intent.getStringExtra("extra_file_name"))
    }

    @Test
    fun `newIntent with selectedItems puts them as ArrayList extra`() {
        val selectedItems = listOf("folder/a.txt", "folder/b.jpg")

        val intent = ExtractionService.newIntent(
            context = context,
            archiveUri = ResourcePath.FileSystem("file:///archive.zip"),
            fileName = "archive.zip",
            selectedItems = selectedItems
        )

        val extras = intent.getStringArrayListExtra("extra_selected_items")
        assertNotNull("selectedItems extra must be present", extras)
        assertEquals(selectedItems, extras)
    }

    @Test
    fun `newIntent with null selectedItems omits extra`() {
        val intent = ExtractionService.newIntent(
            context = context,
            archiveUri = ResourcePath.FileSystem("file:///archive.zip"),
            fileName = "archive.zip",
            selectedItems = null
        )

        assertNull("selectedItems extra must be absent when null",
            intent.getStringArrayListExtra("extra_selected_items"))
    }

    @Test
    fun `newIntent has FLAG_GRANT_READ_URI_PERMISSION for content URI`() {
        val intent = ExtractionService.newIntent(
            context = context,
            archiveUri = ResourcePath.FileSystem("content://com.sec.android.app.myfiles/sdcard/archive.zip"),
            fileName = "archive.zip"
        )

        assertTrue("FLAG_GRANT_READ_URI_PERMISSION must be set",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `newIntent has FLAG_GRANT_READ_URI_PERMISSION for file URI too`() {
        val intent = ExtractionService.newIntent(
            context = context,
            archiveUri = ResourcePath.FileSystem("file:///storage/emulated/0/archive.zip"),
            fileName = "archive.zip"
        )

        // FLAG is always set (not conditional on URI scheme)
        assertTrue("FLAG_GRANT_READ_URI_PERMISSION must always be set",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `newIntent with empty selectedItems puts empty ArrayList`() {
        val intent = ExtractionService.newIntent(
            context = context,
            archiveUri = ResourcePath.FileSystem("file:///archive.zip"),
            fileName = "archive.zip",
            selectedItems = emptyList()
        )

        // emptyList() is not null, so extra must be set
        val extras = intent.getStringArrayListExtra("extra_selected_items")
        assertNotNull("Empty list must put extra (not null)", extras)
        assertTrue("Extra must be empty", extras!!.isEmpty())
    }

    @Test
    fun `newStopIntent has STOP action`() {
        val intent = ExtractionService.newStopIntent(context)

        assertEquals("app.otter.service.STOP_EXTRACTION", intent.action)
    }
}
