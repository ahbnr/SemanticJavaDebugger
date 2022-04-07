@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import org.koin.core.component.KoinComponent
import java.nio.file.Path

class SourcePathCommand : REPLCommand(name = "sourcepath"), KoinComponent {
    val path: Path by argument().path(mustExist = true, mustBeReadable = true)

    override fun run() {
        state.sourcePath = path

        logger.success("Source path set.")
    }
}