package de.ahbnr.semanticweb.java_debugger.repl.commands.infercommands

import kotlin.streams.asSequence

class ClassesOfCommand : ExpressionSubCommand(name = "classesOf") {
    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()
        val evaluator = getEvaluator()

        val classes = evaluator.getClassesOf(rawDlExpression)?.asSequence() ?: emptySequence()

        var foundClass = false
        for (owlClass in classes) {
            foundClass = true
            logger.log(knowledgeBase.asPrefixNameUri(owlClass.iri.iriString))
        }

        if (!foundClass)
            logger.error("The individual belongs to no class. This should never happen, since every individual should be a member of owl:Thing.")
    }
}