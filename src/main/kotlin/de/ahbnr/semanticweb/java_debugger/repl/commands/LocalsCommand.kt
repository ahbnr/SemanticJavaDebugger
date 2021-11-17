package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.debugging.JVMDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.NumberFormatException

class LocalsCommand(
    val jvmDebugger: JVMDebugger
): IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "locals"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        val jvm = jvmDebugger.jvm
        if (jvm == null) {
            logger.error("No JVM is running.")
            return
        }

        val state = jvm.state
        if (state == null) {
            logger.error("JVM is currently not paused.")
            return
        }

        if (state.pausedThread.frameCount() == 0) {
            logger.error("JVM has not started yet.")
            return
        }

        val frame = state.pausedThread.frame(0)

        val visibleVariables = frame.getValues(frame.visibleVariables())
        for ((key, value) in visibleVariables) {
            logger.log(key.name() + " = " + value)
        }
    }
}