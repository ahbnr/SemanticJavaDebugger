@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.choice
import de.ahbnr.semanticweb.java_debugger.repl.ReasonerId
import org.koin.core.component.KoinComponent

class ReasonerCommand : REPLCommand(name = "reasoner"), KoinComponent {
    val reasonerName: String? by argument().choice(*ReasonerId.availableReasoners.map { it.name }.toTypedArray())
        .optional()

    override fun run() {
        when (val it = reasonerName) {
            null -> {
                logger.log("Currently using ${state.targetReasoner.name} reasoner.")
            }
            else -> {
                val reasonerId = ReasonerId.availableReasoners.find { reasonerId -> reasonerId.name == it }

                if (reasonerId == null) {
                    logger.error("No such reasoner is available.")
                    throw ProgramResult(-1)
                }

                state.targetReasoner = reasonerId
            }
        }
    }
}