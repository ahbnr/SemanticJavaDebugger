@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import de.ahbnr.semanticweb.jdi2owl.linting.LinterMode
import de.ahbnr.semanticweb.jdi2owl.linting.ModelSanityChecker
import de.ahbnr.semanticweb.sjdb.repl.commands.utils.ConsistencyChecker
import openllet.jena.PelletInfGraph
import org.koin.core.component.KoinComponent


class CheckKBCommand : REPLCommand(name = "checkkb"), KoinComponent {
    private val checkIfConsistent: Boolean by option("--is-consistent").flag(default = false)
    private val checkForUnsatisfiableClasses: Boolean by option("--has-unsatisfiable-classes").flag(default = false)
    private val runLinters: Boolean by option("--run-linters").flag(default = false)

    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        if (checkForUnsatisfiableClasses && !checkIfConsistent) {
            logger.error("Can only check for unsatisfiable classes if we also check for consistency with --is-consistent.")
            throw ProgramResult(-1)
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
            val checker = ConsistencyChecker(
                knowledgeBase = knowledgeBase
            )

            checker
                .check()
                .use { result ->
                    when (result) {
                        is ConsistencyChecker.Result.Consistent -> logger.success("Knowledge base is consistent.")
                        is ConsistencyChecker.Result.Inconsistent -> {
                            logger.error("Knowledge base is inconsistent!")
                            result.explain()
                        }
                    }
                }
        }

        if (runLinters) {
            if (
                !ModelSanityChecker()
                    .fullCheck(knowledgeBase.ontology, knowledgeBase.buildParameters.limiter, LinterMode.FullReport)
            ) {
                logger.error("Found lints!")
            }
        }
    }
}