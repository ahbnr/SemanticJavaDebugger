@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.ClassCloser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CloseClass : REPLCommand(name = "close"), KoinComponent {
    private val logger: Logger by inject()

    val owlClass: String by argument()

    val noReasoner by option().flag(default = false)

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        val classCloser = ClassCloser(
            knowledgeBase = knowledgeBase,
            noReasoner = noReasoner,
            quiet = false
        )

        try {
            classCloser.close(owlClass)
        } catch (e: IllegalArgumentException) {
            throw ProgramResult(-1)
        }
    }
}