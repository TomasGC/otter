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
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Implementation of ArchiveBrowserRepository using 7-Zip JBinding.
 */
class ArchiveBrowserRepositoryImpl(
    private val context: Context,
) : ArchiveBrowserRepository {

    override suspend fun listEntries(archivePath: ResourcePath, path: String): Result<List<ArchiveEntry>> {
        return withContext(Dispatchers.IO) {
            try {
                val archiveUri = ResourcePathConverter.toUri(archivePath)
                val archiveFile = getFileFromUri(archiveUri) ?: return@withContext Result.failure(
                    IllegalArgumentException("Cannot access archive file")
                )

                val entries = mutableListOf<ArchiveEntry>()
                val seenDirectories = mutableSetOf<String>()

                RandomAccessFile(archiveFile, "r").use { randomAccessFile ->
                    val inStream = RandomAccessFileInStream(randomAccessFile)
                    val inArchive: IInArchive = SevenZip.openInArchive(null, inStream)

                    try {
                        val normalizedPath = path.trim('/').let { if (it.isEmpty()) "" else "$it/" }

                        for (i in 0 until inArchive.numberOfItems) {
                            val itemPath = inArchive.getStringProperty(i, net.sf.sevenzipjbinding.PropID.PATH) ?: continue
                            val isDirectory = inArchive.getSimpleInterface().getArchiveItem(i).isFolder

                            // Check if entry is at current path level
                            if (!itemPath.startsWith(normalizedPath)) continue

                            val relativePath = itemPath.removePrefix(normalizedPath)
                            if (relativePath.isEmpty()) continue

                            val firstSlash = relativePath.indexOf('/')
                            val isDirectChild = firstSlash == -1 || firstSlash == relativePath.length - 1

                            if (isDirectChild) {
                                // Direct file or directory
                                val name = relativePath.trimEnd('/')
                                val size = if (!isDirectory) {
                                    inArchive.getSimpleInterface().getArchiveItem(i).size
                                } else null

                                entries.add(
                                    ArchiveEntry(
                                        path = itemPath.trimEnd('/'),
                                        name = name,
                                        isDirectory = isDirectory,
                                        sizeBytes = size,
                                        compressedSize = null,
                                        lastModified = System.currentTimeMillis()
                                    )
                                )
                            } else {
                                // Nested item - add parent directory if not seen
                                val dirName = relativePath.substring(0, firstSlash)
                                val dirPath = normalizedPath + dirName

                                if (seenDirectories.add(dirPath)) {
                                    entries.add(
                                        ArchiveEntry(
                                            path = dirPath,
                                            name = dirName,
                                            isDirectory = true,
                                            sizeBytes = null,
                                            compressedSize = null,
                                            lastModified = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                    } finally {
                        inArchive.close()
                    }
                }

                Result.success(entries.distinctBy { it.path })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun extractSelected(
        archivePath: ResourcePath,
        entryPaths: List<String>,
        destinationPath: ResourcePath,
    ): Flow<ExtractionProgress> = flow {
        emit(ExtractionProgress.Idle)

        try {
            val archiveUri = ResourcePathConverter.toUri(archivePath)
            val archiveFile = getFileFromUri(archiveUri) ?: throw IllegalArgumentException(
                "Cannot access archive file"
            )
            val destinationUri = ResourcePathConverter.toUri(destinationPath)
            val destinationFile = File(destinationUri.path ?: throw IllegalArgumentException(
                "Invalid destination path"
            ))

            if (!destinationFile.exists()) {
                destinationFile.mkdirs()
            }

            RandomAccessFile(archiveFile, "r").use { randomAccessFile ->
                val inStream = RandomAccessFileInStream(randomAccessFile)
                val inArchive: IInArchive = SevenZip.openInArchive(null, inStream)

                try {
                    val pathSet = entryPaths.toSet()
                    var extractedCount = 0

                    for (i in 0 until inArchive.numberOfItems) {
                        val itemPath = inArchive.getStringProperty(i, net.sf.sevenzipjbinding.PropID.PATH) ?: continue
                        val normalizedItemPath = itemPath.trimEnd('/')

                        if (normalizedItemPath !in pathSet) continue

                        val isDirectory = inArchive.getSimpleInterface().getArchiveItem(i).isFolder
                        val outputFile = File(destinationFile, itemPath)

                        if (isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()

                            val extractionResult = IntArray(1)
                            inArchive.extract(intArrayOf(i), false, object : net.sf.sevenzipjbinding.IArchiveExtractCallback {
                                override fun setTotal(total: Long) {}
                                override fun setCompleted(complete: Long) {}

                                override fun getStream(
                                    index: Int,
                                    extractAskMode: net.sf.sevenzipjbinding.ExtractAskMode
                                ): net.sf.sevenzipjbinding.ISequentialOutStream? {
                                    if (extractAskMode != net.sf.sevenzipjbinding.ExtractAskMode.EXTRACT) {
                                        return null
                                    }

                                    return net.sf.sevenzipjbinding.ISequentialOutStream { data ->
                                        FileOutputStream(outputFile, true).use { it.write(data) }
                                        data.size
                                    }
                                }

                                override fun prepareOperation(extractAskMode: net.sf.sevenzipjbinding.ExtractAskMode) {}

                                override fun setOperationResult(
                                    extractOperationResult: net.sf.sevenzipjbinding.ExtractOperationResult
                                ) {
                                    extractionResult[0] = if (extractOperationResult == net.sf.sevenzipjbinding.ExtractOperationResult.OK) {
                                        1
                                    } else {
                                        0
                                    }
                                }
                            })

                            if (extractionResult[0] == 1) {
                                extractedCount++
                                emit(
                                    ExtractionProgress.Extracting(
                                        currentFile = itemPath,
                                        extractedCount = extractedCount,
                                        totalCount = entryPaths.size,
                                        progress = extractedCount.toFloat() / entryPaths.size
                                    )
                                )
                            }
                        }
                    }

                    emit(ExtractionProgress.Success(destinationFile.absolutePath, extractedCount))
                } finally {
                    inArchive.close()
                }
            }
        } catch (e: Exception) {
            emit(ExtractionProgress.Error(e.message ?: "Extraction failed", e))
        }
    }.flowOn(Dispatchers.IO)

    private fun getFileFromUri(uri: Uri): File? {
        return when (uri.scheme) {
            "file" -> File(uri.path ?: return null)
            "content" -> {
                // Copy to cache if content URI
                val cacheFile = File(context.cacheDir, "temp_archive_${System.currentTimeMillis()}")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                cacheFile
            }
            else -> null
        }
    }
}
