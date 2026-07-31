package app.otter.data.extractor

import net.sf.sevenzipjbinding.PropID
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MultiVolumeCallbackUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `getStream returns stream for existing volume file`() {
        val dir = tempFolder.newFolder("vols")
        dir.resolve("archive.7z.001").writeBytes(ByteArray(16) { it.toByte() })

        val callback = MultiVolumeCallback(dir)
        val stream = callback.getStream("archive.7z.001")

        assertNotNull("Should return stream for existing volume", stream)
        callback.close()
    }

    @Test
    fun `getStream returns null for missing volume file`() {
        val dir = tempFolder.newFolder("vols")

        val callback = MultiVolumeCallback(dir)
        val stream = callback.getStream("archive.7z.002")

        assertNull("Should return null when volume file does not exist", stream)
        callback.close()
    }

    @Test
    fun `getProperty returns null for any PropID`() {
        val dir = tempFolder.newFolder("vols")
        val callback = MultiVolumeCallback(dir)

        assertNull(callback.getProperty(PropID.NAME))
        assertNull(callback.getProperty(PropID.SIZE))
        callback.close()
    }

    @Test
    fun `close releases all opened streams without error`() {
        val dir = tempFolder.newFolder("vols")
        dir.resolve("archive.7z.001").writeBytes(ByteArray(16) { it.toByte() })
        dir.resolve("archive.7z.002").writeBytes(ByteArray(16) { it.toByte() })

        val callback = MultiVolumeCallback(dir)
        callback.getStream("archive.7z.001")
        callback.getStream("archive.7z.002")

        callback.close()
    }

    @Test
    fun `close is idempotent — calling twice does not throw`() {
        val dir = tempFolder.newFolder("vols")
        val callback = MultiVolumeCallback(dir)

        callback.close()
        callback.close()
    }

    @Test
    fun `getStream does not allow path traversal outside baseDir`() {
        val dir = tempFolder.newFolder("vols")
        val sibling = tempFolder.newFile("sensitive.txt")
        sibling.writeText("secret")

        val callback = MultiVolumeCallback(dir)
        val stream = callback.getStream("../sensitive.txt")

        assertNull("Path traversal via '../' must not open files outside baseDir", stream)
        callback.close()
    }
}
