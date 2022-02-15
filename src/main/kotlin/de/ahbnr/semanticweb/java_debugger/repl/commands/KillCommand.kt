@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import org.koin.core.component.KoinComponent

class KillCommand : REPLCommand(name = "kill"), KoinComponent {
    override fun run() {
        if (jvmDebugger.jvm == null) {
            logger.error("There is no JVM running.")
            throw ProgramResult(-1)
        }

        jvmDebugger.kill()
    }
}