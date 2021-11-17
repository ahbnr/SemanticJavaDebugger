@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.file.FileSystems
import kotlin.io.path.exists

class DomainCommand: IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "domain"

    private val usage = """
        Usage: domain <owl file path>
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        if (argv.size != 1) {
            logger.error(usage)
            return
        }

        val domainFile = argv[0]
        if (!FileSystems.getDefault().getPath(domainFile).exists()) {
            logger.error("No such file.")
            return
        }

        repl.applicationDomainDefFile = domainFile
        logger.log("Will load application domain from $domainFile.")
    }
}