@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import de.ahbnr.semanticweb.sjdb.repl.commands.utils.ClassCloser
import org.koin.core.component.KoinComponent

class CloseClass : REPLCommand(name = "close"), KoinComponent {
    val owlClass: String by argument()

    val noReasoner by option().flag(default = false)

    class SyntacticExtractionOptions : OptionGroup() {
        val classRelationDepth by option().int().default(-1)
    }

    val moduleExtraction by option().groupChoice(
        "syntactic" to SyntacticExtractionOptions()
    )

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        val classCloser = ClassCloser(
            knowledgeBase = knowledgeBase,
            noReasoner = noReasoner,
            doSyntacticExtraction = moduleExtraction is SyntacticExtractionOptions,
            classRelationDepth = moduleExtraction.let { if (it is SyntacticExtractionOptions) it.classRelationDepth else -1 },
            quiet = false
        )

        try {
            classCloser.close(owlClass)
        } catch (e: IllegalArgumentException) {
            throw ProgramResult(-1)
        }
    }
}