@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import de.ahbnr.semanticweb.java_debugger.repl.ReasonerId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReasonerCommand : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "reasoner"

    private val usage = "reasoner [set <${ReasonerId.availableReasoners.joinToString("|") { it.name }}>]"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        if (argv.isEmpty()) {
            logger.log("Currently using ${repl.targetReasoner.name} reasoner.")
            return true
        } else if (argv.size == 2 && argv[0] == "set") {
            val reasonerId = ReasonerId.availableReasoners.find { it.name == argv[1] }

            if (reasonerId == null) {
                logger.error("No such reasoner is available.")
                return false
            }

            repl.targetReasoner = reasonerId

            return true
        } else {
            logger.error(usage)
            return false
        }
    }
}