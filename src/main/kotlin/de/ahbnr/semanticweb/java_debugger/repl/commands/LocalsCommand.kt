@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LocalsCommand(
    private val jvmDebugger: JvmDebugger
) : REPLCommand(name = "locals"), KoinComponent {
    val logger: Logger by inject()


    override fun run() {
        val jvm = jvmDebugger.jvm
        if (jvm == null) {
            logger.error("No JVM is running.")
            throw ProgramResult(-1)
        }

        val jvmState = jvm.state
        if (jvmState == null) {
            logger.error("JVM is currently not paused.")
            throw ProgramResult(-1)
        }

        if (jvmState.pausedThread.frameCount() == 0) {
            logger.error("JVM has not started yet.")
            throw ProgramResult(-1)
        }

        val frame = jvmState.pausedThread.frame(0)

        val visibleVariables = frame.getValues(frame.visibleVariables())
        for ((key, value) in visibleVariables) {
            logger.log(key.name() + " = " + value)
        }
    }
}