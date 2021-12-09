@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.FileInputStream
import java.io.FileNotFoundException

class ReadCommand : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "read"

    private val usage = "read <command file>"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        if (argv.size != 1) {
            logger.error(usage)
            return false
        }

        val commandFile = argv.first()

        val fileInputStream = try {
            FileInputStream(commandFile)
        } catch (e: FileNotFoundException) {
            logger.error("No such file: $commandFile.")
            return false
        }

        return repl.interpretStream(fileInputStream)
    }
}