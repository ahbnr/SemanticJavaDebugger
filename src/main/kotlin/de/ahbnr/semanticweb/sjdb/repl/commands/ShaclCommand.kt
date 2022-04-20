@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import de.ahbnr.semanticweb.sjdb.repl.commands.utils.ShaclValidator
import org.koin.core.component.KoinComponent
import java.io.File


class ShaclCommand: REPLCommand(name = "shacl"), KoinComponent {
    private val shapesFile: File by argument().file(
        mustExist = true, mustBeReadable = true, canBeDir = false
    )

    private val noReasoner by option()
        .flag(default = false)
        .help("dont validate an OWL inference model (might require full realization if a non-Jena OWL reasoner is used) but validate the current knowledge base as it is.")

    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        val validator = ShaclValidator(
            knowledgeBase = knowledgeBase,
            dontUseReasoner = noReasoner,
            quiet = false
        )

        validator.validate(shapesFile, createFocusVariables = true)
    }
}