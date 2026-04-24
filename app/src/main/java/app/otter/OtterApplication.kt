package app.otter

import android.app.Application
import app.otter.util.FileLoggingTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class with Hilt initialization and Timber setup
 */
@HiltAndroidApp
class OtterApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Setup Timber logging
        // Debug builds: log to Logcat + file
        Timber.plant(Timber.DebugTree())
        Timber.plant(FileLoggingTree(this))
        Timber.d("Timber initialized with file logging")
    }
}
