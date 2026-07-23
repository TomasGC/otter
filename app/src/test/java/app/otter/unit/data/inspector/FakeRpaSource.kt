package app.otter.data.inspector

import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Fake RPA file source for unit testing.
 * Provides RPA bytes from memory instead of disk.
 */
class FakeRpaSource(private val bytes: ByteArray) : RpaFileSource {
    var openCount = 0

    override fun openInputStream(): InputStream {
        openCount++
        return ByteArrayInputStream(bytes)
    }

    override fun length(): Long = bytes.size.toLong()
}
