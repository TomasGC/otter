package app.otter.data.extractor

import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback
import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Volume callback for multi-volume RAR and 7z archives.
 *
 * 7-Zip-JBinding calls [getStream] for each volume file name it needs to open.
 * This callback resolves volume names relative to [baseDir] and opens them as
 * [RandomAccessFileInStream] instances. All opened streams are tracked so they
 * can be closed after extraction via [close].
 *
 * Naming conventions handled transparently by 7-Zip-JBinding:
 *  - RAR old-style:  archive.rar, archive.r00, archive.r01, ...
 *  - RAR new-style:  archive.part1.rar, archive.part2.rar, ...
 *  - 7z:             archive.7z.001, archive.7z.002, ...
 */
internal class MultiVolumeCallback(private val baseDir: File) : IArchiveOpenVolumeCallback {

    private val openStreams = mutableListOf<RandomAccessFileInStream>()

    @Throws(SevenZipException::class)
    override fun getProperty(propID: PropID): Any? = null

    @Throws(SevenZipException::class)
    override fun getStream(filename: String): IInStream? {
        val volumeFile = File(baseDir, filename)
        // Reject path traversal attempts (e.g., "../secret") that escape baseDir
        if (!volumeFile.canonicalPath.startsWith(baseDir.canonicalPath + File.separator) &&
            volumeFile.canonicalPath != baseDir.canonicalPath) return null
        if (!volumeFile.exists() || !volumeFile.canRead()) return null
        val stream = RandomAccessFileInStream(RandomAccessFile(volumeFile, "r"))
        openStreams.add(stream)
        return stream
    }

    fun close() {
        openStreams.forEach { runCatching { it.close() } }
        openStreams.clear()
    }
}
