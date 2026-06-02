package app.otter.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Hilt module providing coroutine dispatchers for dependency injection.
 *
 * Allows testing with TestDispatcher by overriding in test modules.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    /**
     * Provides IO dispatcher for background operations.
     *
     * @return Dispatchers.IO for production, TestDispatcher in tests
     */
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
