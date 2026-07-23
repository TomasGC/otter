package app.otter.data.inspector

import java.io.InputStream

interface RpaFileSource {
    fun openInputStream(): InputStream
    fun length(): Long
}
