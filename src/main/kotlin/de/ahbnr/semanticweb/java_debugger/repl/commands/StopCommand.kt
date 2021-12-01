@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class StopCommand(
    val jvmDebugger: JvmDebugger
) : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "stop"

    private val usage = """
        Usage: stop at <full class name>:<line_number>
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        if (argv.size != 2) {
            logger.error(usage)
            return false
        }

        when (argv[0]) {
            "at" -> {
                val split = argv[1].split(':')

                if (split.size != 2) {
                    logger.error(usage)
                    return false
                }

                val (className, lineNumber) = split

                try {
                    jvmDebugger.setBreakpoint(className, lineNumber.toInt())
                } catch (e: NumberFormatException) {
                    logger.error("Line number must be an integer: $lineNumber")
                    return false
                }
            }
            else -> {
                logger.error(usage)
                return false
            }
        }

        return true
    }
}