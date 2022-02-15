@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.choice
import org.koin.core.component.KoinComponent

class LogCommand : REPLCommand(name = "log"), KoinComponent {
    private val ontologyChangesMode = "ontology-changes"
    private val reasonerMode = "reasoner"
    private val mode by argument().choice(ontologyChangesMode, reasonerMode)

    private val on = "on"
    private val off = "off"
    private val onOff by argument().choice(on, off).optional()

    private fun logState(enabled: Boolean) {
        if (enabled) logger.success("enabled")
        else logger.error("disabled")
    }

    override fun run() {
        when (mode) {
            ontologyChangesMode -> {
                if (onOff != null) {
                    state.logOntologyChanges = onOff == on
                }

                logState(state.logOntologyChanges)
            }

            reasonerMode -> {
                if (onOff != null) {
                    state.logReasoner = onOff == on
                }

                logState(state.logReasoner)
            }
        }
    }
}