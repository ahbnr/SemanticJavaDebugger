package de.ahbnr.semanticweb.java_debugger.repl.commands.infercommands

import de.ahbnr.semanticweb.java_debugger.repl.commands.REPLCommand
import org.semanticweb.owlapi.reasoner.InferenceType

class ClassificationCommand : REPLCommand(name = "classification") {
    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        knowledgeBase
            .getOwlClassExpressionReasoner(knowledgeBase.ontology)
            .use { reasoner ->
                // Classification is the task of computing all entailed class subsumptions between named classes

                // Based on OWL2Bench code:
                // https://github.com/kracr/owl2bench/blob/master/Experiments/java%20runnable%20jar%20files/Java%20codes%20of%20the%20runnable%20jars/hermit/src/main/java/debug/hermit/Hermit.java
                reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)

                // ORE 2013 does it via InferredSubClassAxiomGenerator.
                // Not sure what is better
                // https://github.com/owlcs/ore-framework
            }

        logger.success("done.")
    }
}