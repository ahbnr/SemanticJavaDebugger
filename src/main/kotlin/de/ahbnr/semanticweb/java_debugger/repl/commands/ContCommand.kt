@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ContCommand(
    val jvmDebugger: JvmDebugger
) : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "cont"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val jvm = jvmDebugger.jvm

        if (jvm == null) {
            logger.error("There is no JVM running.")
            return false
        }

        jvm.resume()
        return true
    }
}