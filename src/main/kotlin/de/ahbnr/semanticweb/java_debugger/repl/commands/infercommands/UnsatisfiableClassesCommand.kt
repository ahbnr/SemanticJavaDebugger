package de.ahbnr.semanticweb.java_debugger.repl.commands.infercommands

import de.ahbnr.semanticweb.java_debugger.repl.commands.REPLCommand

class UnsatisfiableClassesCommand : REPLCommand(name = "unsatisfiableClasses") {
    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        knowledgeBase
            .getOwlClassExpressionReasoner(knowledgeBase.ontology)
            .use { reasoner ->
                // FIXME: Is this the correct way of doing it?
                val unsat = reasoner.unsatisfiableClasses.entitiesMinusBottom
                if (unsat.isEmpty()) {
                    logger.success("No unsatisfiable concepts except the default bottom concepts.")
                } else {
                    logger.error("There are unsatisfiable concepts in the ontology besides the default bottom concepts:")
                    logger.error(unsat.toString())
                }
            }

    }
}