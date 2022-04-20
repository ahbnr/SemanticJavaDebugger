@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands.assertcommands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import de.ahbnr.semanticweb.sjdb.repl.commands.REPLCommand
import de.ahbnr.semanticweb.sjdb.repl.commands.utils.OwlExpressionEvaluator
import de.ahbnr.semanticweb.sjdb.repl.commands.utils.ShaclValidator
import de.ahbnr.semanticweb.sjdb.utils.UsabilityPreprocessor
import org.koin.core.component.KoinComponent
import java.io.File

class ShaclAssertCommand : REPLCommand(name = "shacl"), KoinComponent {
    private val conforms = "conforms"
    private val violation = "violation"

    private val mode: String by argument()
        .choice( conforms, violation )

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
            quiet = true
        )

        val validationSuccess = validator.validate(shapesFile, createFocusVariables = false)

        val assertionOk = when(mode) {
            conforms -> validationSuccess
            violation -> !validationSuccess
            else -> throw RuntimeException("Unknown shacl assertion mode. This should be prevented by Clikt argument checks and therefore should never happen.")
        }

        if (assertionOk)
            logger.success("PASSED.")
        else {
            logger.error("FAILED!")
            throw ProgramResult(-1)
        }
    }
}