package de.ahbnr.semanticweb.java_debugger.repl.commands.utils

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.repl.CloseableOWLReasoner
import de.ahbnr.semanticweb.java_debugger.repl.KnowledgeBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.apibinding.OWLFunctionalSyntaxFactory
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxParserImpl
import org.semanticweb.owlapi.manchestersyntax.renderer.ParserException
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException
import org.semanticweb.owlapi.util.mansyntax.ManchesterOWLSyntaxParser
import java.util.stream.Stream
import kotlin.streams.asSequence

class OwlExpressionEvaluator(
    val knowledgeBase: KnowledgeBase,
    val quiet: Boolean
) : KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    var doSyntacticExtraction: Boolean = false
    var classRelationDepth: Int = -1

    private fun buildParser(): ManchesterOWLSyntaxParser {
        val manchesterParser = ManchesterOWLSyntaxParserImpl(
            knowledgeBase.ontology.owlOntologyManager.ontologyConfigurator,
            knowledgeBase.ontology.owlOntologyManager.owlDataFactory
        )
        for ((prefixName, prefixUri) in knowledgeBase.prefixNameToUri) {
            manchesterParser.prefixManager.setPrefix(prefixName, prefixUri)
        }
        manchesterParser.setDefaultOntology(knowledgeBase.ontology)

        return manchesterParser
    }

    private fun replaceVariables(expression: String): String =
        // Replace every occurence of a variable with the associated IRI
        expression.replace(
            Regex("\\?[^\\s]+")
        ) { match ->
            val varName = match.value
            val rdfNode = knowledgeBase.getVariable(varName)
            val iri = if (rdfNode != null && rdfNode.isURIResource) {
                // Workaround: Use prefixed IRI when possible, since manchester parser seems not to accept <...> IRIs in all cases
                //   might be a bug in the parser
                val rawUri = rdfNode.asResource().uri
                val prefixedUri = knowledgeBase.asPrefixNameUri(rawUri)
                if (prefixedUri != rawUri)
                    prefixedUri
                else
                    "<$rawUri>"
            } else {
                logger.error("There is no variable $varName.")
                null
            }

            iri ?: varName
        }

    fun parseAxiomExpression(manchesterAxiomExpression: String): OWLAxiom? {
        val parser = buildParser()

        val axiom = try {
            parser.setStringToParse(
                replaceVariables(
                    manchesterAxiomExpression
                )
            )
            parser.parseAxiom()
        } catch (e: ParserException) {
            val message = e.message
            if (message != null) {
                logger.log(message)
                logger.log("")
            }
            logger.error("Could not parse Manchester axiom expression.")
            return null
        }

        return axiom
    }

    fun parseClassExpression(manchesterClassExpression: String): OWLClassExpression? {
        val parser = buildParser()

        val classExpression = try {
            parser.parseClassExpression(
                replaceVariables(manchesterClassExpression)
            )
        } catch (e: ParserException) {
            val message = e.message
            if (message != null) {
                logger.log(message)
                logger.log("")
            }
            logger.error("Could not parse Manchester class expression.")
            return null
        }

        return classExpression
    }

    private fun getReasoner(signature: Stream<OWLEntity>): CloseableOWLReasoner {
        val ontology =
            if (doSyntacticExtraction) {
                val extractor = SyntacticLocalityModuleExtractor(
                    knowledgeBase,
                    classRelationDepth,
                    quiet
                )
                val module = extractor.extractModule(
                    signature.asSequence().toSet()
                )

                module
            } else knowledgeBase.ontology

        return knowledgeBase.getOwlClassExpressionReasoner(ontology)
    }

    fun <T> handleReasonerErrors(action: () -> T): T? =
        try {
            action.invoke()
        } catch (e: InconsistentOntologyException) {
            val message = e.message
            if (message != null) logger.log(message)
            logger.error("The ontology is not consistent.")

            null
        }

    // Attention! This does not check, whether the class returns an individual for the current knowledge base.
    // It checks whether there is *some* knowledge base such that the class is non-empty.
    // (in line with the open world assumption)
    fun isSatisfiable(manchesterClassExpression: String): Boolean? {
        val classExpression = parseClassExpression(manchesterClassExpression) ?: return null

        return getReasoner(classExpression.signature())
            .use { reasoner ->
                handleReasonerErrors {
                    reasoner.isSatisfiable(classExpression)
                }
            }
    }

    fun getRepresentativeInstances(classExpression: OWLClassExpression): Stream<OWLNamedIndividual>? =
        getReasoner(classExpression.signature())
            .use { reasoner ->
                handleReasonerErrors {
                    reasoner.representativeInstances(classExpression)
                }
            }

    fun getRepresentativeInstances(manchesterClassExpression: String): Stream<OWLNamedIndividual>? {
        val classExpression = parseClassExpression(manchesterClassExpression) ?: return null

        return getRepresentativeInstances(classExpression)
    }

    fun getInstances(classExpression: OWLClassExpression): Stream<OWLNamedIndividual>? =
        getReasoner(classExpression.signature())
            .use { reasoner ->
                handleReasonerErrors {
                    // We make a quick satisfiability check.
                    // Those are usually faster than listing instances and we can return the empty set if the class
                    // is not satisfiable
                    // FIXME: Why is this the case?
                    if (reasoner.isSatisfiable(classExpression))
                        reasoner.instances(classExpression)
                    else Stream.empty()
                }
            }

    fun getInstances(manchesterClassExpression: String): Stream<OWLNamedIndividual>? {
        val classExpression = parseClassExpression(manchesterClassExpression) ?: return null

        return getInstances(classExpression)
    }

    fun isEntailed(axiom: OWLAxiom, explain: Boolean = false): Boolean? =
        getReasoner(axiom.signature())
            .use { reasoner ->
                handleReasonerErrors {
                    val result = reasoner.isEntailed(axiom)

                    if (explain) {
                        logger.log(
                            if (result) "Why is the axiom entailed?"
                            else "Why is the axiom not entailed?"
                        )
                        val explanationGenerator =
                            knowledgeBase.getExplanationGenerator(reasoner.rootOntology)
                        // knowledgeBase.getExplanationGenerator(reasoner.rootOntology, reasoner) // Use this to force the same reasoner

                        val explanations = explanationGenerator.invoke(axiom)
                        for (explanation in explanations) {
                            logger.log(explanation.toString())
                        }
                    }

                    result
                }
            }

    fun isEntailed(manchesterAxiomExpression: String, explain: Boolean = false): Boolean? {
        val axiom = parseAxiomExpression(manchesterAxiomExpression) ?: return null

        return isEntailed(axiom, explain)
    }

    fun getClassesOf(individual: OWLNamedIndividual): Stream<OWLClass>? =
        getReasoner(individual.signature())
            .use { reasoner ->
                handleReasonerErrors {
                    reasoner
                        .getTypes(
                            individual,
                            false
                        )
                        .entities()
                }
            }

    fun getClassesOf(prefixedIndividualIri: String): Stream<OWLClass>? {
        val resolvedIndividualIri = knowledgeBase.resolvePrefixNameInUri(prefixedIndividualIri)

        val individual = knowledgeBase.ontology.asGraphModel().getIndividual(resolvedIndividualIri)
        if (individual == null) {
            logger.error("There is no such named individual.")
            return null
        }

        return getClassesOf(
            OWLFunctionalSyntaxFactory.NamedIndividual(IRI.create(resolvedIndividualIri))
        )
    }
}