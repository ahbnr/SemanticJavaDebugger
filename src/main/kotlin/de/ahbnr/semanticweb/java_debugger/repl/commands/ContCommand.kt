@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.debugging.JVMDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.NumberFormatException

class ContCommand(
    val jvmDebugger: JVMDebugger
): IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "cont"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        val jvm = jvmDebugger.jvm

        if (jvm == null) {
            logger.error("There is no JVM running.")
            return
        }

        jvm.resume()
    }
}