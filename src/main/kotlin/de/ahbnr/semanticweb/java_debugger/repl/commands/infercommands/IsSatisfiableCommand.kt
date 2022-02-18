package de.ahbnr.semanticweb.java_debugger.repl.commands.infercommands

import com.github.ajalt.clikt.core.ProgramResult

class IsSatisfiableCommand : ExpressionSubCommand(name = "isSatisfiable") {
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