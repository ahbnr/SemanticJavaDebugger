@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.OwlExpressionEvaluator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.streams.asSequence

class OwlCommand : REPLCommand(name = "owl"), KoinComponent {
    private val logger: Logger by inject()

    class SyntacticExtractionOptions : OptionGroup() {
        val classRelationDepth by option().int().default(-1)
    }

    val moduleExtraction by option().groupChoice(
        "syntactic" to SyntacticExtractionOptions()
    )

    private val instancesOfMode = "instancesOf"
    private val entailsMode = "entails"
    private val isSatisfiableMode = "isSatisfiable"
    val mode by argument().choice(instancesOfMode, entailsMode, isSatisfiableMode)

    val rawDlExpression: String by argument()

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        val evaluator = OwlExpressionEvaluator(knowledgeBase, quiet = false)
        when (val it = moduleExtraction) {
            is SyntacticExtractionOptions -> {
                evaluator.doSyntacticExtraction = true
                evaluator.classRelationDepth = it.classRelationDepth
            }
        }

        when (mode) {
            instancesOfMode -> {
                val instances = evaluator.getInstances(rawDlExpression) ?: throw ProgramResult(-1)

                if (instances.isEmpty) {
                    logger.log("Found no instances for this class.")
                } else {
                    val individuals = instances
                        .asSequence()
                        .flatMap { it.entities().asSequence() }

                    for ((individualIdx, individual) in individuals.withIndex()) {
                        val prefixUriAndName =
                            knowledgeBase.uriToPrefixName.entries.find { (uri, _) ->
                                individual.iri.startsWith(uri)
                            }

                        val prefixedIri = if (prefixUriAndName != null) {
                            val (prefixUri, prefixName) = prefixUriAndName

                            individual.iri.replaceRange(prefixUri.indices, "$prefixName:").toString()
                        } else individual.iri.toString()


                        logger.log(prefixedIri, appendNewline = false)

                        val rdfGraph = knowledgeBase.ontology.asGraphModel()
                        val rdfResource = rdfGraph.getResource(individual.iri.toString())
                        if (rdfGraph.containsResource(rdfResource)) {
                            val varName = "?i$individualIdx"
                            knowledgeBase.setVariable(varName, rdfResource)

                            logger.debug(" as $varName")
                        } else
                            logger.warning(" (no RDF node found)")
                    }
                }
            }
            entailsMode -> {
                val entails = evaluator.isEntailed(rawDlExpression) ?: throw ProgramResult(-1)
                if (entails) {
                    logger.success("true")
                } else {
                    logger.error("false")
                }
            }
            isSatisfiableMode -> {
                val isSatisfiable = evaluator.isSatisfiable(rawDlExpression) ?: throw ProgramResult(-1)
                if (isSatisfiable) {
                    logger.success("true")
                } else {
                    logger.error("false")
                }
            }
        }
    }
}