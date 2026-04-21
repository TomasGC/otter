package app.otter.di

import android.app.Application
import android.content.Context
import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.RarExtractor
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
    fun provideExtractors(): List<ArchiveExtractor> = listOf(
        ZipExtractor(),
        RarExtractor()
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
        context: Context
    ): FileBrowserRepository = FileBrowserRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideArchiveBrowserRepository(
        context: Context
    ): ArchiveBrowserRepository = ArchiveBrowserRepositoryImpl(context)

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
