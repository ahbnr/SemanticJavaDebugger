@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.file.Path

class ClassPathCommand : REPLCommand(name = "classpath"), KoinComponent {
    private val logger: Logger by inject()

    val path: Path by argument().path(mustExist = true, mustBeReadable = true)

    override fun run() {
        state.classPath = path

        logger.success("Classpath set.")
    }
}