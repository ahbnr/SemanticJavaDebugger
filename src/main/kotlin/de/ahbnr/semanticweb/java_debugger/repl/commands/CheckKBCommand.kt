@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import openllet.core.OpenlletOptions
import openllet.jena.PelletInfGraph
import openllet.owlapi.OpenlletReasoner
import openllet.owlapi.explanation.PelletExplanation
import openllet.owlapi.explanation.io.manchester.ManchesterSyntaxExplanationRenderer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.PrintWriter


class CheckKBCommand(
    private val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "checkkb"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val knowledgeBase = repl.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base is available. Run `buildkb` first.")
            return false
        }

        val checkIfConsistent = argv.contains("--is-consistent")
        val checkForUnsatisfiableClasses = argv.contains("--has-unsatisfiable-classes")
        if (checkForUnsatisfiableClasses && !checkIfConsistent) {
            logger.error("Can only check for unsatisfiable classes if we also check for consistency with --is-consistent.")
            return false
        }

        logger.log("${knowledgeBase.ontology.axiomCount} axioms.")

        logger.log("Performing basic validation on inference model...")
        org.apache.jena.shared.impl.JenaParameters.enableEagerLiteralValidation = true
        org.apache.jena.shared.impl.JenaParameters.enableSilentAcceptanceOfUnknownDatatypes = false
        org.apache.jena.shared.impl.JenaParameters.enableOWLRuleOverOWLRuleWarnings = true
        org.apache.jena.shared.impl.JenaParameters.enablePlainLiteralSameAsString = true
        org.apache.jena.shared.impl.JenaParameters.enableWhitespaceCheckingOfTypedLiterals = false
        val infModel = knowledgeBase.getJenaValidationModel()

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
                if (it.extension != null) {
                    logger.log(it.extension.toString())
                }

                val graph = infModel.graph
                if (graph is PelletInfGraph) {
                    val clash = graph.kb.aBox.lastClash
                    if (clash != null) {
                        logger.log(clash.detailedString())
                        logger.log("Offending node:")
                        logger.log(clash.node.toString())
                    }
                }

                logger.log("")
            }
        }

        if (checkIfConsistent) {
            // Needed to enable explanations for Openllet, see internals of PelletExplanation.setup() method.
            // We manipulate it directly so that we can deactivate it later.
            val originalTracingSetting = OpenlletOptions.USE_TRACING
            OpenlletOptions.USE_TRACING = true
            try {
                val reasoner = knowledgeBase.getConsistencyReasoner()

                logger.log("Performing consistency check...")
                val isConsistent = reasoner.isConsistent
                if (isConsistent) {
                    logger.success("Knowledge base is consistent.")
                } else {
                    logger.error("Knowledge base is inconsistent!")

                    if (reasoner is OpenlletReasoner) {
                        val renderer = ManchesterSyntaxExplanationRenderer()
                        val out = PrintWriter(logger.logStream())
                        renderer.startRendering(out)

                        val explainer = PelletExplanation(reasoner)
                        logger.emphasize("Why is the knowledge base inconsistent?")
                        logger.emphasize("")
                        renderer.render(explainer.inconsistencyExplanations)

                        renderer.endRendering()
                    } else {
                        logger.debug("(Explanations are available when using Openllet reasoner.)")
                    }
                }

                if (checkForUnsatisfiableClasses) {
                    if (!isConsistent) {
                        logger.error("Can only do a full check if Ontology is consistent.")
                        return false
                    }

                    // FIXME: Am I using Hermit correctly here?
                    val unsat = reasoner.unsatisfiableClasses.entitiesMinusBottom
                    if (unsat.isEmpty()) {
                        logger.success("No unsatisfiable concepts except the default bottom concepts.")
                    } else {
                        logger.error("There are unsatisfiable concepts in the ontology besides the default bottom concepts:")
                        logger.error(unsat.toString())
                    }
                }
            } finally {
                OpenlletOptions.USE_TRACING = originalTracingSetting
            }
        }

        return true
    }
}