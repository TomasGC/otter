package app.otter.data.extractor

import java.io.File

/**
 * Factory for creating IZipFileReader instances.
 * Abstraction allows injection of mock readers in tests.
 */
interface IZipFileReaderFactory {
    /**
     * Create a ZIP file reader for the given file.
     */
    fun create(file: File): IZipFileReader
}

/**
 * Real factory implementation that creates RealZipFileReader instances.
 */
class RealZipFileReaderFactory : IZipFileReaderFactory {
    override fun create(file: File): IZipFileReader {
        return RealZipFileReader(file)
    }
}
