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
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.OwlExpressionEvaluator
import org.koin.core.component.KoinComponent
import org.semanticweb.owlapi.apibinding.OWLFunctionalSyntaxFactory
import org.semanticweb.owlapi.model.OWLNamedIndividual
import kotlin.streams.asSequence

class OwlCommand : REPLCommand(name = "owl"), KoinComponent {
    class SyntacticExtractionOptions : OptionGroup() {
        val classRelationDepth by option().int().default(-1)
    }

    val moduleExtraction by option().groupChoice(
        "syntactic" to SyntacticExtractionOptions()
    )

    private val instancesOfMode = "instancesOf"
    private val representativeInstancesOfMode = "representativeInstancesOf"
    private val entailsMode = "entails"
    private val isSatisfiableMode = "isSatisfiable"
    private val isClosedMode = "isClosed"
    private val signatureMode = "signature"
    private val classesOfMode = "classesOf"
    val mode by argument().choice(
        representativeInstancesOfMode,
        instancesOfMode,
        entailsMode,
        isSatisfiableMode,
        isClosedMode,
        signatureMode,
        classesOfMode
    )

    val rawDlExpression: String by argument()

    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        val evaluator = OwlExpressionEvaluator(knowledgeBase, quiet = false)
        when (val it = moduleExtraction) {
            is SyntacticExtractionOptions -> {
                evaluator.doSyntacticExtraction = true
                evaluator.classRelationDepth = it.classRelationDepth
            }
        }

        when (mode) {
            instancesOfMode, representativeInstancesOfMode -> {
                val instances =
                    when (mode) {
                        instancesOfMode -> evaluator.getInstances(rawDlExpression)
                        representativeInstancesOfMode -> evaluator.getRepresentativeInstances(rawDlExpression)
                        else -> null
                    }
                        ?.asSequence()
                        ?: throw ProgramResult(-1)

                var foundInstances = false
                for ((individualIdx, individual) in instances.withIndex()) {
                    foundInstances = true

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

                if (!foundInstances) {
                    logger.log("Found no instances for this class.")
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
            isClosedMode -> {
                // TODO: Can this be done more efficiently?

                val classExpression = evaluator.parseClassExpression(rawDlExpression) ?: throw ProgramResult(-1)

                val instances = evaluator
                    .getInstances(classExpression)
                    ?.toArray { size -> arrayOfNulls<OWLNamedIndividual>(size) }
                    ?: throw ProgramResult(-1)

                val isClosedAxiom = OWLFunctionalSyntaxFactory.SubClassOf(
                    classExpression,
                    OWLFunctionalSyntaxFactory.ObjectOneOf(*instances)
                )

                val isClosed = evaluator.isEntailed(isClosedAxiom) ?: throw ProgramResult(-1)
                if (isClosed) {
                    logger.success("true")
                } else {
                    logger.error("false")
                }
            }
            signatureMode -> {
                val classExpression = evaluator.parseClassExpression(rawDlExpression) ?: throw ProgramResult(-1)

                for (owlEntity in classExpression.signature()) {
                    logger.log(owlEntity.toString())
                }
            }
            classesOfMode -> {
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
    }
}