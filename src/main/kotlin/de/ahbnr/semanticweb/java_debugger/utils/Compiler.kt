package de.ahbnr.semanticweb.java_debugger.utils

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.file.Path
import java.util.*
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.io.path.absolutePathString


class Compiler(
    private val sources: List<Path>,
    private val destination: Path
) : KoinComponent {
    private val logger: Logger by inject()

    fun compile() {
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject>()

        val fileManager =
            compiler.getStandardFileManager(diagnostics, Locale.getDefault(), null)

        val javaObjects = fileManager.getJavaFileObjects(*sources.map { it.toFile() }.toTypedArray())
        if (javaObjects.none()) {
            throw RuntimeException("There is nothing to compile.")
        }

        val compileOptions = listOf(
            "-d", destination.absolutePathString(),
            "-g" // debug
        )

        val compilerTask = compiler.getTask(
            null,
            fileManager,
            diagnostics,
            compileOptions,
            null,
            javaObjects
        )

        if (!compilerTask.call()) {
            for (diagnostic in diagnostics.diagnostics) {
                logger.error("Error at ${diagnostic.source.name}:${diagnostic.lineNumber}:")
                logger.error(diagnostic.toString())
                logger.error("")
            }

            throw RuntimeException("Compilation failed!")
        }
    }
}