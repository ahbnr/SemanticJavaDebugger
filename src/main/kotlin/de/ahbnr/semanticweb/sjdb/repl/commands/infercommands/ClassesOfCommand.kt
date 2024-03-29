package de.ahbnr.semanticweb.sjdb.repl.commands.infercommands

import kotlin.streams.asSequence

class ClassesOfCommand : ExpressionSubCommand(
    name = "classesOf",
    help = """
        Lists all OWL classes an individual belongs to.
    """.trimIndent()
) {
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