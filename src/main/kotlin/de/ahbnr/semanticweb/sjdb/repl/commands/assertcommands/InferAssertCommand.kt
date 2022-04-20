@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands.assertcommands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.types.choice
import de.ahbnr.semanticweb.sjdb.repl.commands.REPLCommand
import de.ahbnr.semanticweb.sjdb.repl.commands.utils.OwlExpressionEvaluator
import de.ahbnr.semanticweb.sjdb.utils.UsabilityPreprocessor
import org.koin.core.component.KoinComponent

class InferAssertCommand : REPLCommand(name = "infer"), KoinComponent {
    private val isSatisfiableMode = "isSatisfiable"
    private val isUnsatisfiableMode = "isUnsatisfiable"
    private val entailsMode = "entails"
    private val entailsNotMode = "entailsNot"
    private val findsInstances = "findsInstances"
    private val findsNoInstances = "findsNoInstances"

    private val mode: String by argument()
        .choice(
            isSatisfiableMode, isUnsatisfiableMode,
            entailsMode, entailsNotMode,
            findsInstances, findsNoInstances
        )

    private val manchesterSyntaxExpression: String by argument()
        .convert { UsabilityPreprocessor.preprocess(it) }

    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

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

            findsInstances, findsNoInstances -> {
                val foundInstances = evaluator
                    .getInstances(manchesterSyntaxExpression)
                    ?.findAny()
                    ?.isPresent
                    ?: throw ProgramResult(-1)

                if (mode == findsInstances)
                    foundInstances
                else
                    !foundInstances
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