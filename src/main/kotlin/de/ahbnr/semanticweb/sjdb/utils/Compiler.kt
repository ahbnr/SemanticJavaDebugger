package de.ahbnr.semanticweb.sjdb.utils

import de.ahbnr.semanticweb.sjdb.logging.Logger
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.FileFilterUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.file.Path
import java.util.*
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.io.path.absolutePathString
import kotlin.io.path.isRegularFile


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

        val javaSourceFiles = sources.flatMap {
            if (it.isRegularFile()) {
                listOf(it.toFile())
            } else {
                FileUtils.listFiles(
                    it.toFile(),
                    FileFilterUtils.suffixFileFilter(".java"),
                    TrueFileFilter.INSTANCE
                )
            }
        }.toTypedArray()

        val javaObjects = fileManager.getJavaFileObjects(*javaSourceFiles)
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