package de.ahbnr.semanticweb.sjdb.repl.commands.infercommands

import com.github.ajalt.clikt.core.ProgramResult

class IsSatisfiableCommand: ExpressionSubCommand(
    name = "isSatisfiable",
    help = """
        Determines whether the given OWL class is satisfiable.
        That is, it determines if there is some knowledge base such that the class is not
        empty.
    """.trimIndent()
) {
    override fun run() {
        val evaluator = getEvaluator()

        val isSatisfiable = evaluator.isSatisfiable(rawDlExpression) ?: throw ProgramResult(-1)
        if (isSatisfiable) {
            logger.success("true")
        } else {
            logger.error("false")
        }
    }
}