@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import org.koin.core.component.KoinComponent

class ContCommand : REPLCommand(name = "cont"), KoinComponent {
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