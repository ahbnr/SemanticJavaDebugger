package de.ahbnr.semanticweb.sjdb.repl.commands.infercommands

import de.ahbnr.semanticweb.sjdb.repl.commands.REPLCommand
import org.semanticweb.owlapi.model.parameters.Imports

class RealisationCommand : REPLCommand(
    name = "realisation",
    help = """
        Computes all entailed class assertions for named OWL classes and individuals in the
        knowledge base.
        That is, it computes all instances of all OWL classes.
        It may be useful to benchmark reasoner performance on a knowledge base.
    """.trimIndent()
) {
    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        knowledgeBase
            .getDefaultOWLReasoner(knowledgeBase.ontology)
            .use { reasoner ->
                // Based on OWL2Bench code:
                // https://github.com/kracr/owl2bench/blob/master/Experiments/java%20runnable%20jar%20files/Java%20codes%20of%20the%20runnable%20jars/hermit/src/main/java/debug/hermit/Hermit.java
                for (individual in knowledgeBase.ontology.individualsInSignature(Imports.INCLUDED)) {
                    reasoner.getTypes(individual, false)
                }
            }

        logger.success("done.")
    }
}