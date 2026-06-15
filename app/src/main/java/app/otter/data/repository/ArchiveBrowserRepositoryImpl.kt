package app.otter.data.repository

import android.content.Context
import android.net.Uri
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.ArchiveEntry
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ResourcePath
import app.otter.domain.repository.ArchiveBrowserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class ArchiveBrowserRepositoryImpl(
    private val context: Context,
    private val pathValidator: app.otter.util.PathValidator,
) : ArchiveBrowserRepository {

    companion object {
        private const val TEMP_ARCHIVE_PREFIX = "temp_archive_"
    }

    override suspend fun listEntries(archivePath: ResourcePath, path: String): Result<List<ArchiveEntry>> {
        return withContext(Dispatchers.IO) {
            var tempCacheFile: File? = null
            try {
                val archiveUri = ResourcePathConverter.toUri(archivePath)
                val archiveFile = getFileFromUri(archiveUri) ?: return@withContext Result.failure(
                    IllegalArgumentException("Cannot access archive file")
                )
                if (archiveUri.scheme == "content") tempCacheFile = archiveFile

                val entries = mutableListOf<ArchiveEntry>()
                val seenDirectories = mutableSetOf<String>()

                RandomAccessFile(archiveFile, "r").use { raf ->
                    val inArchive: IInArchive = SevenZip.openInArchive(null, RandomAccessFileInStream(raf))
                    try {
                        val normalizedPath = path.trim('/').let { if (it.isEmpty()) "" else "$it/" }
                        for (i in 0 until inArchive.numberOfItems) {
                            val itemPath = inArchive.getStringProperty(i, PropID.PATH) ?: continue
                            processArchiveEntry(i, itemPath, normalizedPath, inArchive, entries, seenDirectories)
                        }
                    } finally {
                        inArchive.close()
                    }
                }

                Result.success(entries.distinctBy { it.path })
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                tempCacheFile?.delete()
            }
        }
    }

    private fun processArchiveEntry(
        index: Int,
        itemPath: String,
        normalizedPath: String,
        inArchive: IInArchive,
        entries: MutableList<ArchiveEntry>,
        seenDirectories: MutableSet<String>
    ) {
        val relativePath = itemPath.removePrefix(normalizedPath).takeIf {
            itemPath.startsWith(normalizedPath) && it.isNotEmpty()
        } ?: return

        val isDirectory = inArchive.getSimpleInterface().getArchiveItem(index).isFolder
        val firstSlash = relativePath.indexOf('/')
        val isDirectChild = firstSlash == -1 || firstSlash == relativePath.length - 1

        if (isDirectChild) {
            val size = if (!isDirectory) inArchive.getSimpleInterface().getArchiveItem(index).size else null
            entries.add(ArchiveEntry(
                path = itemPath.trimEnd('/'), isDirectory = isDirectory,
                sizeBytes = size ?: 0L, compressedSize = 0L, lastModified = System.currentTimeMillis()
            ))
        } else {
            val dirPath = normalizedPath + relativePath.substring(0, firstSlash)
            if (seenDirectories.add(dirPath)) {
                entries.add(ArchiveEntry(
                    path = dirPath, isDirectory = true, sizeBytes = 0L,
                    compressedSize = 0L, lastModified = System.currentTimeMillis()
                ))
            }
        }
    }

    override fun extractSelected(
        archivePath: ResourcePath,
        entryPaths: List<String>,
        destinationPath: ResourcePath,
    ): Flow<ExtractionProgress> = flow {
        emit(ExtractionProgress.Idle)

        var tempCacheFile: File? = null
        try {
            val archiveUri = ResourcePathConverter.toUri(archivePath)
            val archiveFile = getFileFromUri(archiveUri)
                ?: throw IllegalArgumentException("Cannot access archive file")
            if (archiveUri.scheme == "content") tempCacheFile = archiveFile

            val destinationFile = File(
                ResourcePathConverter.toUri(destinationPath).path
                    ?: throw IllegalArgumentException("Invalid destination path")
            )
            if (!destinationFile.exists()) destinationFile.mkdirs()

            RandomAccessFile(archiveFile, "r").use { raf ->
                val inArchive: IInArchive = SevenZip.openInArchive(null, RandomAccessFileInStream(raf))
                try {
                    val pathSet = entryPaths.toSet()
                    var extractedCount = 0

                    for (i in 0 until inArchive.numberOfItems) {
                        val itemPath = inArchive.getStringProperty(i, PropID.PATH)
                            ?.takeIf { it.trimEnd('/') in pathSet } ?: continue

                        val isDirectory = inArchive.getSimpleInterface().getArchiveItem(i).isFolder
                        val outputFile = File(destinationFile, itemPath)
                        pathValidator.validatePath(outputFile, destinationFile, itemPath)

                        if (isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            if (extractFileEntry(inArchive, i, outputFile)) {
                                extractedCount++
                                emit(ExtractionProgress.Extracting(
                                    currentFile = itemPath, extractedCount = extractedCount,
                                    totalCount = entryPaths.size,
                                    progress = extractedCount.toFloat() / entryPaths.size
                                ))
                            }
                        }
                    }

                    emit(ExtractionProgress.Success(destinationFile.absolutePath, extractedCount))
                } finally {
                    inArchive.close()
                }
            }
        } catch (e: Exception) {
            emit(ExtractionProgress.Error("${e::class.simpleName}: ${e.message ?: "Extraction failed"}", e))
        } finally {
            tempCacheFile?.delete()
        }
    }.flowOn(Dispatchers.IO)

    private fun extractFileEntry(inArchive: IInArchive, index: Int, outputFile: File): Boolean {
        val extractionResult = IntArray(1)
        inArchive.extract(intArrayOf(index), false, object : IArchiveExtractCallback {
            private var currentOutputStream: FileOutputStream? = null

            override fun setTotal(total: Long) = Unit
            override fun setCompleted(complete: Long) = Unit
            override fun prepareOperation(extractAskMode: ExtractAskMode) = Unit

            override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
                if (extractAskMode != ExtractAskMode.EXTRACT) return null
                currentOutputStream = FileOutputStream(outputFile)
                return ISequentialOutStream { data ->
                    checkNotNull(currentOutputStream) { "Output stream closed unexpectedly" }.write(data)
                    data.size
                }
            }

            override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
                try { currentOutputStream?.close() } finally { currentOutputStream = null }
                extractionResult[0] = if (extractOperationResult == ExtractOperationResult.OK) 1 else 0
            }
        })
        return extractionResult[0] == 1
    }

    private fun getFileFromUri(uri: Uri): File? {
        return when (uri.scheme) {
            "file" -> File(uri.path ?: return null)
            "content" -> {
                val cacheFile = File(context.cacheDir, "$TEMP_ARCHIVE_PREFIX${System.currentTimeMillis()}")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                cacheFile
            }
            else -> null
        }
    }
}
