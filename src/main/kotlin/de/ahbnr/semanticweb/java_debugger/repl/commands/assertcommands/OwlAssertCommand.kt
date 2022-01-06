@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands.assertcommands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.choice
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.commands.REPLCommand
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.OwlExpressionEvaluator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class OwlAssertCommand : REPLCommand(name = "owl"), KoinComponent {
    private val logger: Logger by inject()

    private val isSatisfiableMode = "isSatisfiable"
    private val isUnsatisfiableMode = "isUnsatisfiable"
    private val entailsMode = "entails"
    private val entailsNotMode = "entailsNot"
    private val mode: String by argument().choice(isSatisfiableMode, isUnsatisfiableMode, entailsMode, entailsNotMode)
    private val manchesterSyntaxExpression: String by argument()

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        val evaluator = OwlExpressionEvaluator(knowledgeBase, true)

        val hasPassed: Boolean = when (mode) {
            isSatisfiableMode, isUnsatisfiableMode -> {
                val isSatisfiable = evaluator.isSatisfiable(manchesterSyntaxExpression) ?: throw ProgramResult(-1)

                if (mode == isSatisfiableMode) {
                    isSatisfiable
                } else {
                    !isSatisfiable
                }
            }

            entailsMode, entailsNotMode -> {
                val isEntailed = evaluator.isEntailed(manchesterSyntaxExpression) ?: throw ProgramResult(-1)

                if (mode == entailsMode)
                    isEntailed
                else
                    !isEntailed
            }

            else -> {
                logger.error("No such mode: $mode.")
                throw ProgramResult(-1)
            }
        }

        if (hasPassed)
            logger.success("SUCCESS")
        else
            logger.error("FAILED")

        throw ProgramResult(if (hasPassed) 0 else -1)
    }
}