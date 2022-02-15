@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import org.koin.core.component.KoinComponent

class SectionCommand : REPLCommand(name = "section"), KoinComponent {
    private val sectionText: String? by argument().optional()

    override fun run() {
        logger.emphasize("")
        val sectionText = sectionText

        if (sectionText.isNullOrBlank()) {
            logger.emphasize("**********************")
        } else {
            logger.emphasize("*".repeat(sectionText.length + 6))
            logger.emphasize("*  $sectionText  *")
            logger.emphasize("*".repeat(sectionText.length + 6))
        }

        logger.emphasize("")
    }
}