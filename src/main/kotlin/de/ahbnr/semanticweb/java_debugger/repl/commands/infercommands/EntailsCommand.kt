package de.ahbnr.semanticweb.java_debugger.repl.commands.infercommands

import com.github.ajalt.clikt.core.ProgramResult

class EntailsCommand : ExpressionSubCommand(name = "entails") {
    override fun run() {
        val evaluator = getEvaluator()

        val entails = evaluator.isEntailed(rawDlExpression) ?: throw ProgramResult(-1)
        if (entails) {
            logger.success("true")
        } else {
            logger.error("false")
        }
    }
}