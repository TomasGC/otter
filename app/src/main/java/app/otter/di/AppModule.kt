package app.otter.di

import android.app.Application
import android.content.Context
import app.otter.data.extractor.ApacheGzipExtractor
import app.otter.data.extractor.ApacheTarExtractor
import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.ArchiveLibraryManager
import app.otter.data.extractor.ExtractionLogger
import app.otter.data.extractor.ITempFileManager
import app.otter.data.extractor.RarExtractor
import app.otter.data.extractor.SevenZipExtractor
import app.otter.data.extractor.SevenZipExtractorHelper
import app.otter.data.extractor.StandardProgressCalculator
import app.otter.data.extractor.TempFileManager
import app.otter.data.extractor.ZipExtractor
import app.otter.data.repository.ArchiveBrowserRepositoryImpl
import app.otter.data.repository.ArchiveRepositoryImpl
import app.otter.data.repository.FileBrowserRepositoryImpl
import app.otter.domain.repository.ArchiveBrowserRepository
import app.otter.domain.repository.ArchiveRepository
import app.otter.domain.repository.FileBrowserRepository
import app.otter.domain.usecase.BrowseArchiveUseCase
import app.otter.domain.usecase.BrowseFilesUseCase
import app.otter.domain.usecase.ExtractArchiveUseCase
import app.otter.util.MimeTypeUtil
import app.otter.util.PathValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(application: Application): Context =
        application.applicationContext

    @Provides
    @Singleton
    fun provideTempFileManager(): ITempFileManager = TempFileManager()

    @Provides
    @Singleton
    fun provideSevenZipHelper(): SevenZipExtractorHelper =
        SevenZipExtractorHelper(StandardProgressCalculator())

    @Provides
    @Singleton
    fun provideExtractors(
        pathValidator: PathValidator,
        archiveLibraryManager: ArchiveLibraryManager,
        tempFileManager: ITempFileManager,
        sevenZipHelper: SevenZipExtractorHelper
    ): List<ArchiveExtractor> = listOf(
        ZipExtractor(pathValidator, tempFileManager, sevenZipHelper),
        RarExtractor(pathValidator, archiveLibraryManager, tempFileManager, sevenZipHelper),
        SevenZipExtractor(pathValidator, archiveLibraryManager, tempFileManager, sevenZipHelper),
        ApacheTarExtractor(pathValidator, tempFileManager, sevenZipHelper),  // Uses Apache Commons Compress (works with InputStream)
        ApacheGzipExtractor(pathValidator, tempFileManager, sevenZipHelper)  // Uses Apache Commons Compress (works with InputStream)
    )

    @Provides
    @Singleton
    fun provideArchiveRepository(
        context: Context,
        extractors: List<@JvmSuppressWildcards ArchiveExtractor>
    ): ArchiveRepository = ArchiveRepositoryImpl(context, extractors)

    @Provides
    @Singleton
    fun provideFileBrowserRepository(
        context: Context,
        mimeTypeUtil: MimeTypeUtil
    ): FileBrowserRepository = FileBrowserRepositoryImpl(context, mimeTypeUtil)

    @Provides
    @Singleton
    fun provideArchiveBrowserRepository(
        context: Context,
        pathValidator: PathValidator
    ): ArchiveBrowserRepository = ArchiveBrowserRepositoryImpl(context, pathValidator)

    @Provides
    fun provideExtractArchiveUseCase(
        repository: ArchiveRepository
    ): ExtractArchiveUseCase = ExtractArchiveUseCase(repository)

    @Provides
    fun provideBrowseFilesUseCase(
        repository: FileBrowserRepository
    ): BrowseFilesUseCase = BrowseFilesUseCase(repository)

    @Provides
    fun provideBrowseArchiveUseCase(
        repository: ArchiveBrowserRepository
    ): BrowseArchiveUseCase = BrowseArchiveUseCase(repository)
}
