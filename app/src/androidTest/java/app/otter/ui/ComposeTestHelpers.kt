package app.otter.ui

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText

/**
 * Helper functions for Compose instrumented tests to handle timing and flakiness.
 */

/**
 * Wait until a node with the given text is displayed.
 *
 * This is more reliable than waitForIdle() for async composables.
 *
 * @param text The text to wait for
 * @param timeoutMillis Maximum time to wait (default: 5000ms)
 * @param substring Whether to match substring (default: false)
 */
fun ComposeTestRule.waitUntilNodeWithTextExists(
    text: String,
    timeoutMillis: Long = 5_000,
    substring: Boolean = false
) {
    waitUntil(timeoutMillis) {
        onAllNodesWithText(text, substring = substring)
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
}

/**
 * Wait until a condition is true or timeout.
 *
 * @param condition The condition to wait for
 * @param timeoutMillis Maximum time to wait (default: 5000ms)
 */
fun ComposeTestRule.waitUntilCondition(
    timeoutMillis: Long = 5_000,
    condition: () -> Boolean
) {
    waitUntil(timeoutMillis, condition)
}
