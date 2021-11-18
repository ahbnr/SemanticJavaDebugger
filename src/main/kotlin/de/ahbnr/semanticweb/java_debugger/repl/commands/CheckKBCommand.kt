@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.HermiT.ReasonerFactory
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException

class CheckKBCommand: IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "checkkb"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        val ontology = repl.knowledgeBase
        if (ontology == null) {
            logger.error("No knowledge base is available. Run `buildkb` first.")
            return
        }

        logger.log("${ontology.axiomCount} axioms.")

        val hermit = ReasonerFactory().createReasoner(ontology)
        val isConsistent = hermit.isConsistent
        if (isConsistent) {
            logger.log("Knowledge base is consistent.")
        }

        else {
            logger.error("Knowledge base is inconsistent!")
        }

        if (argv.firstOrNull() == "full") {
            if (!isConsistent) {
                logger.error("Can only do a full check if Ontology is consistent.")
                return
            }

            // FIXME: Am I using Hermit correctly here?
            val unsat = hermit.unsatisfiableClasses.entitiesMinusBottom
            if (unsat.isEmpty()) {
                logger.log("No unsatisfiable concepts except the default bottom concepts.")
            }

            else {
                logger.error("There are unsatisfiable concepts in the ontology besides the default bottom concepts:")
                logger.error(unsat.toString())
            }
        }
    }
}