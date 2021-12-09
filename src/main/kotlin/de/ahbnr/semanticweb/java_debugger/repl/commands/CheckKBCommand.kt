@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.reasoner.ReasonerRegistry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.HermiT.ReasonerFactory

class CheckKBCommand(
    private val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "checkkb"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val ontology = repl.knowledgeBase.ontology
        if (ontology == null) {
            logger.error("No knowledge base is available. Run `buildkb` first.")
            return false
        }

        logger.log("${ontology.axiomCount} axioms.")

        logger.log("Performing basic validation on inference model...")
        // val reasoner = ReasonerRegistry.getOWLReasoner()
        // val reasoner = ReasonerRegistry.getOWLMiniReasoner()
        val reasoner = ReasonerRegistry.getOWLMicroReasoner()
        val infModel = ontology.asGraphModel().getInferenceModel(reasoner)

        val validityReport = infModel.validate()
        if (validityReport.isValid) {
            logger.success("Model is valid.")
        } else {
            logger.error("Model is invalid!")
        }

        // FIXME: For some reason, Jena does not accept my custom access modifier datatype and outputs this warning:
        //   Culprit is deduced to be of enumerated type (implicicated class) but is not one of the enumerationsn This may be due to aliasing."
        //   Culprit = 'public'^^https://github.com/ahbnr/SemanticJavaDebugger/Java#AccessModifier
        //   Implicated node: *
        // if (validityReport.isClean) {
        //     logger.success("Model is clean.")
        // } else {
        //     logger.error("Model is not clean!")
        // }

        // if (!validityReport.isValid || !validityReport.isClean) {
        if (!validityReport.isValid) {
            validityReport.reports.forEachRemaining {
                logger.log(it.type)
                logger.log(it.description)
                logger.log("")
            }
        }

        var isConsistent = true
        val hermit = ReasonerFactory().createReasoner(ontology)
        if (argv.contains("--is-consistent")) {
            logger.log("Performing consistency check with HermiT...")
            isConsistent = hermit.isConsistent
            if (isConsistent) {
                logger.success("Knowledge base is consistent.")
            } else {
                logger.error("Knowledge base is inconsistent!")
            }
        }

        if (argv.contains("--has-unsatisfiable-classes")) {
            if (!isConsistent) {
                logger.error("Can only do a full check if Ontology is consistent.")
                return false
            }

            // FIXME: Am I using Hermit correctly here?
            val unsat = hermit.unsatisfiableClasses.entitiesMinusBottom
            if (unsat.isEmpty()) {
                logger.success("No unsatisfiable concepts except the default bottom concepts.")
            } else {
                logger.error("There are unsatisfiable concepts in the ontology besides the default bottom concepts:")
                logger.error(unsat.toString())
            }
        }

        return true
    }
}