package de.ahbnr.semanticweb.logging

import java.io.OutputStream

interface Logger {
    fun debug(line: String, appendNewline: Boolean = true)
    fun log(line: String, appendNewline: Boolean = true)
    fun emphasize(line: String, appendNewline: Boolean = true)
    fun success(line: String, appendNewline: Boolean = true)
    fun warning(line: String, appendNewline: Boolean = true)
    fun error(line: String, appendNewline: Boolean = true)

    fun logStream(): OutputStream
    fun warningStream(): OutputStream
    fun errorStream(): OutputStream
    fun successStream(): OutputStream
}