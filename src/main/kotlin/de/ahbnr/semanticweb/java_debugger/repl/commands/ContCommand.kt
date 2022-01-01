@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ContCommand(
    private val jvmDebugger: JvmDebugger
) : REPLCommand(name = "cont"), KoinComponent {
    val logger: Logger by inject()


    override fun run() {
        val jvm = jvmDebugger.jvm

        if (jvm == null) {
            logger.error("There is no JVM running.")
            throw ProgramResult(-1)
        }

        logger.debug("Resuming program and deleting old knowledge base.")
        jvm.resume()
        this.state.knowledgeBase = null
    }
}