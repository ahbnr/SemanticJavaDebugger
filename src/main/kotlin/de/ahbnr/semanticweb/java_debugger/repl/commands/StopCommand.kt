@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class StopCommand(
    val jvmDebugger: JvmDebugger
) : REPLCommand(name = "stop"), KoinComponent {
    val logger: Logger by inject()

    inner class AtSubCommand : REPLCommand(name = "at") {
        val classAndLine: String by argument()

        override fun run() {
            val split = classAndLine.split(':')

            if (split.size != 2) {
                this@StopCommand.logger.error(getFormattedUsage())
                throw ProgramResult(-1)
            }

            val (className, lineNumber) = split

            try {
                this@StopCommand.jvmDebugger.setBreakpoint(className, lineNumber.toInt())
            } catch (e: NumberFormatException) {
                this@StopCommand.logger.error("Line number must be an integer: $lineNumber")
                throw ProgramResult(-1)
            }
        }
    }

    init {
        subcommands(AtSubCommand())
    }

    override fun run() {}
}