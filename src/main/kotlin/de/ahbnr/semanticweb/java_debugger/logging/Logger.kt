package de.ahbnr.semanticweb.java_debugger.logging

import java.io.OutputStream

interface Logger {
    fun log(line: String)
    fun error(line: String)

    fun logStream(): OutputStream
    fun errorStream(): OutputStream
}