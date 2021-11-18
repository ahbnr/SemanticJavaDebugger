@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RunCommand(
    val jvmDebugger: JvmDebugger
): IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "run"

    private val usage = """
        Usage: run <class>
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        if (argv.size != 1) {
            logger.error(usage)
            return
        }

        jvmDebugger.launchVM(argv[0])
    }
}