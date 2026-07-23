package app.otter.di

import android.app.Application
import android.content.Context
import app.otter.data.extractor.GzipExtractor
import app.otter.data.extractor.TarExtractor
import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.ArchiveLibraryManager
import app.otter.data.extractor.ArchiveSizeGuard
import app.otter.data.extractor.ExtractionLogger
import app.otter.data.extractor.ITempFileManager
import app.otter.data.extractor.IZipFileReaderFactory
import app.otter.data.extractor.RarExtractor
import app.otter.data.extractor.RealZipFileReaderFactory
import app.otter.data.extractor.RpaExtractor
import app.otter.data.extractor.SevenZipExtractor
import app.otter.data.extractor.SevenZipExtractorHelper
import app.otter.data.extractor.StandardProgressCalculator
import app.otter.data.extractor.TempFileManager
import app.otter.data.extractor.ZipExtractor
import app.otter.data.browser.FileSystemBrowser
import app.otter.data.inspector.ArchiveInspectorFactory
import app.otter.data.repository.ArchiveRepositoryImpl
import app.otter.data.repository.ItemBrowserRepositoryImpl
import app.otter.domain.repository.ArchiveRepository
import app.otter.domain.repository.ItemBrowserRepository
import app.otter.domain.usecase.BrowseItemsUseCase
import app.otter.domain.usecase.ExtractArchiveUseCase
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
    fun provideZipFileReaderFactory(): IZipFileReaderFactory = RealZipFileReaderFactory()

    @Provides
    @Singleton
    fun provideArchiveSizeGuardFactory(): () -> ArchiveSizeGuard = { ArchiveSizeGuard() }

    @Provides
    @Singleton
    fun provideExtractors(
        pathValidator: PathValidator,
        archiveLibraryManager: ArchiveLibraryManager,
        tempFileManager: ITempFileManager,
        sevenZipHelper: SevenZipExtractorHelper,
        zipFileReaderFactory: IZipFileReaderFactory,
        sizeGuardFactory: () -> ArchiveSizeGuard
    ): List<ArchiveExtractor> = listOf(
        ZipExtractor(pathValidator, tempFileManager, sevenZipHelper, zipFileReaderFactory, sizeGuardFactory),
        RarExtractor(pathValidator, archiveLibraryManager, tempFileManager, sevenZipHelper, sizeGuardFactory),
        SevenZipExtractor(pathValidator, archiveLibraryManager, tempFileManager, sevenZipHelper, sizeGuardFactory),
        TarExtractor(pathValidator, tempFileManager, sevenZipHelper, sizeGuardFactory),
        GzipExtractor(tempFileManager, sevenZipHelper, sizeGuardFactory),
        RpaExtractor(pathValidator, tempFileManager, sevenZipHelper, sizeGuardFactory)
    )

    @Provides
    @Singleton
    fun provideArchiveRepository(
        context: Context,
        extractors: List<@JvmSuppressWildcards ArchiveExtractor>
    ): ArchiveRepository = ArchiveRepositoryImpl(context, extractors)

    @Provides
    fun provideExtractArchiveUseCase(
        repository: ArchiveRepository
    ): ExtractArchiveUseCase = ExtractArchiveUseCase(repository)

    @Provides
    @Singleton
    fun provideBrowseItemsUseCase(
        repository: ItemBrowserRepository
    ): BrowseItemsUseCase = BrowseItemsUseCase(repository)

    @Provides
    @Singleton
    fun provideItemBrowserRepository(
        fileSystemBrowser: FileSystemBrowser,
        inspectorFactory: ArchiveInspectorFactory
    ): ItemBrowserRepository =
        ItemBrowserRepositoryImpl(fileSystemBrowser, inspectorFactory)
}
