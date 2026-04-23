package app.otter.domain.model

/**
 * Represents a path to a file or directory resource.
 * Platform-agnostic alternative to Android's Uri for the domain layer.
 *
 * Uses value class for zero runtime overhead while maintaining type safety.
 *
 * @property value The string representation of the path
 */
@JvmInline
value class ResourcePath(val value: String) {
    companion object {
        /**
         * Creates a ResourcePath from a string.
         */
        fun from(string: String): ResourcePath = ResourcePath(string)
    }

    override fun toString(): String = value
}
