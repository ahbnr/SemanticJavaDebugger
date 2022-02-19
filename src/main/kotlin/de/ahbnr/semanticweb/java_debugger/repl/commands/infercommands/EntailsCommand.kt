package de.ahbnr.semanticweb.java_debugger.repl.commands.infercommands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class EntailsCommand : ExpressionSubCommand(name = "entails") {
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