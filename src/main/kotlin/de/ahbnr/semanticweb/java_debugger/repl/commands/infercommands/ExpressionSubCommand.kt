package de.ahbnr.semanticweb.java_debugger.repl.commands.infercommands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import de.ahbnr.semanticweb.java_debugger.repl.commands.REPLCommand
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.OwlExpressionEvaluator

abstract class ExpressionSubCommand(name: String) : REPLCommand(name = name) {
    protected class SyntacticExtractionOptions : OptionGroup() {
        val classRelationDepth by option().int().default(-1)
    }

    protected val moduleExtraction by option().groupChoice(
        "syntactic" to SyntacticExtractionOptions()
    )

    val rawDlExpression: String by argument()

    protected fun getEvaluator(): OwlExpressionEvaluator {
        val knowledgeBase = tryGetKnowledgeBase()

        val evaluator = OwlExpressionEvaluator(knowledgeBase, quiet = false)
        when (val it = moduleExtraction) {
            is SyntacticExtractionOptions -> {
                evaluator.doSyntacticExtraction = true
                evaluator.classRelationDepth = it.classRelationDepth
            }
        }

        return evaluator
    }
}