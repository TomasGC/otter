package app.otter.data.inspector

import java.io.File
import java.io.InputStream

class FileRpaSource(private val file: File) : RpaFileSource {
    override fun openInputStream(): InputStream = file.inputStream()
    override fun length(): Long = file.length()
}
