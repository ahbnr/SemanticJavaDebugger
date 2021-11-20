@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SectionCommand : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()

    override val name = "section"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        logger.emphasize("")
        if (rawInput.isEmpty()) {
            logger.emphasize("**********************")
        } else {
            logger.emphasize("*".repeat(rawInput.length + 6))
            logger.emphasize("*  $rawInput  *")
            logger.emphasize("*".repeat(rawInput.length + 6))
        }

        logger.emphasize("")
    }
}