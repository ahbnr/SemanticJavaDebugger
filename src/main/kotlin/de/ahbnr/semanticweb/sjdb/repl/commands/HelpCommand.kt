@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import de.ahbnr.semanticweb.sjdb.repl.REPL
import org.koin.core.component.KoinComponent

class HelpCommand: REPLCommand(
    name = "help"
), KoinComponent {
    var repl: REPL? = null

    val command by argument().optional()

    override fun run() {
        val repl = this.repl ?: throw RuntimeException("No REPL reference available. This should never happen. Did you forget to set the repl field when instantiating this command?")

        when (val command = this.command) {
            null -> {
                logger.log("Available commands: ${repl.commandMap.keys.joinToString(", ")}.")
            }

            else -> {
                val commandObj = repl.commandMap.getOrElse(command) {
                    logger.error("No such command.")
                    throw ProgramResult(-1)
                }

                logger.log(commandObj.getFormattedHelp())
            }
        }

    }
}