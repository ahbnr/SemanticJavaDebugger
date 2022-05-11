package de.ahbnr.semanticweb.sjdb.repl.commands.infercommands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class EntailsCommand: ExpressionSubCommand(
    name = "entails",
    help = """
        Checks whether a given axiom is entailed by the current knowledge base.
    """.trimIndent(),
    expressionHelp = """
        Axiom for which it should be determined whether it is entailed by the current
        knowledge base or not.
        
        Because the manchester syntax parser does not provide sufficient support for all
        kinds of axioms, this command expects the axiom to be formulated in functional
        syntax.
    """.trimIndent()
) {
    private val explain by option().flag(default = false)

    override fun run() {
        val evaluator = getEvaluator()

        val entails = evaluator.isEntailed(rawDlExpression, explain) ?: throw ProgramResult(-1)
        if (entails) {
            logger.success("true")
        } else {
            logger.error("false")
        }
    }
}