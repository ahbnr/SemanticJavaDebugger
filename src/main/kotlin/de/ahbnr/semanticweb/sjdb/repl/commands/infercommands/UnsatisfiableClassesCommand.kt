package de.ahbnr.semanticweb.sjdb.repl.commands.infercommands

import de.ahbnr.semanticweb.sjdb.repl.commands.REPLCommand

class UnsatisfiableClassesCommand : REPLCommand(
    name = "unsatisfiableClasses",
    help = "Returns all named OWL classes that are not satisfiable excluding owl:Nothing."
) {
    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        knowledgeBase
            .getDefaultOWLReasoner(knowledgeBase.ontology)
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