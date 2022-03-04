@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.types.path
import org.koin.core.component.KoinComponent
import java.nio.file.Path

class ClassPathsCommand : REPLCommand(name = "classpaths"), KoinComponent {
    private val paths: List<Path> by argument().path(mustExist = true, mustBeReadable = true).multiple()

    override fun run() {
        state.classPaths = paths

        logger.success("Classpath set.")
    }
}