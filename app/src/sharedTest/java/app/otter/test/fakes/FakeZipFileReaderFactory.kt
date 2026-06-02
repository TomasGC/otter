package app.otter.test.fakes

import app.otter.data.extractor.IZipFileReader
import app.otter.data.extractor.IZipFileReaderFactory
import java.io.File

/**
 * Fake factory for creating FakeZipFileReader instances in tests.
 * Returns a fake reader with predefined entries instead of reading real files.
 */
class FakeZipFileReaderFactory(
    private val entries: Map<String, String>  // fileName -> content
) : IZipFileReaderFactory {

    override fun create(file: File): IZipFileReader {
        return FakeZipFileReader(entries)
    }
}
