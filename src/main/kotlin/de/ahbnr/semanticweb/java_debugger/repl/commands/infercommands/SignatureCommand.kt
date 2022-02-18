package de.ahbnr.semanticweb.java_debugger.repl.commands.infercommands

import com.github.ajalt.clikt.core.ProgramResult

class SignatureCommand : ExpressionSubCommand(name = "signature") {
    override fun run() {
        val evaluator = getEvaluator()

        val classExpression = evaluator.parseClassExpression(rawDlExpression) ?: throw ProgramResult(-1)

        for (owlEntity in classExpression.signature()) {
            logger.log(owlEntity.toString())
        }
    }
}