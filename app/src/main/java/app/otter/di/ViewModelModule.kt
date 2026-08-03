package app.otter.di

import app.otter.domain.model.ResourcePath
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule {
    @Provides
    fun provideStartPath(): ResourcePath? = null
}
