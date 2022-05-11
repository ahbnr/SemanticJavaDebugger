package de.ahbnr.semanticweb.sjdb.repl.commands.infercommands

import com.github.ajalt.clikt.core.ProgramResult

class SignatureCommand : ExpressionSubCommand(
    name = "signature",
    help = "Computes the signature of an OWL class.",
    expressionHelp = "OWL class in manchester syntax."
) {
    override fun run() {
        val evaluator = getEvaluator()

        val classExpression = evaluator.parseClassExpression(rawDlExpression) ?: throw ProgramResult(-1)

        for (owlEntity in classExpression.signature()) {
            logger.log(owlEntity.toString())
        }
    }
}