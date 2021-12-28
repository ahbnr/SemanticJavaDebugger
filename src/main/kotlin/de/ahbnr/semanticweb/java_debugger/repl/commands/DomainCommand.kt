@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class DomainCommand : REPLCommand(name = "domain"), KoinComponent {
    val logger: Logger by inject()

    val domainFile: File by argument().file(mustExist = true, mustBeReadable = true)

    override fun run() {
        state.applicationDomainDefFile = domainFile.path
        logger.log("Will load application domain from $domainFile next time buildkb is called.")
    }
}