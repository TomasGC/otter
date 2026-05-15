package app.otter

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Empty activity for Hilt instrumented tests.
 *
 * Must be in debug source set (not androidTest) to be in the same process as the app.
 * Used by createAndroidComposeRule<HiltTestActivity>() in Compose tests.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
