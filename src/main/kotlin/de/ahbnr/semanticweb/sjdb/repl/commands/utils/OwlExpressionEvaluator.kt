package de.ahbnr.semanticweb.sjdb.repl.commands.utils

import de.ahbnr.semanticweb.sjdb.logging.Logger
import de.ahbnr.semanticweb.sjdb.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.sjdb.repl.CloseableOWLReasoner
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.apibinding.OWLFunctionalSyntaxFactory
import org.semanticweb.owlapi.functional.parser.OWLFunctionalSyntaxOWLParserFactory
import org.semanticweb.owlapi.io.OWLParser
import org.semanticweb.owlapi.io.StringDocumentSource
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

    private fun buildManchesterParser(): ManchesterOWLSyntaxParser {
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

    private fun buildFunctionalParser(): OWLParser {
        val factory = OWLFunctionalSyntaxOWLParserFactory()

        return factory.createParser()
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

    // fun parseAxiomExpression(manchesterFramesExpression: String): OWLAxiom? {
    //     val parser = buildManchesterParser()

    //     val axiom = try {
    //         val parsingOntology = knowledgeBase.ontology.owlOntologyManager.createOntology()
    //         try {
    //             parser.setStringToParse(manchesterFramesExpression)

    //             val frames = parser.parseFrames()

    //             frames
    //                 .lastOrNull()
    //                 .let {
    //                     if (it == null) {
    //                         logger.error("A single axiom must be passed.")
    //                         return@parseAxiomExpression null
    //                     }

    //                     it.axiom
    //                 }
    //         } finally {
    //             knowledgeBase.ontology.owlOntologyManager.removeOntology(parsingOntology)
    //         }
    //     } catch (e: ParserException) {
    //         val message = e.message
    //         if (message != null) {
    //             logger.log(message)
    //             logger.log("")
    //         }
    //         logger.error("Could not parse manchester frames expression.")
    //         return null
    //     }

    //     return axiom
    // }

    fun parseAxiomExpression(functionalAxiomExpression: String): OWLAxiom? {
        val parser = buildFunctionalParser()

        // Based on https://stackoverflow.com/a/62684809
        val helperOntologyString = """
            ${
            knowledgeBase
                .prefixNameToUri
                .map { (prefixName, prefixUri) ->
                    "Prefix($prefixName:=<$prefixUri>)"
                }
                .joinToString("\n")
        }
            Ontology(<https://github.com/ahbnr/SemanticJavaDebugger/Parsing/Temp>
                $functionalAxiomExpression
            )
        """.trimIndent()

        val docSource = StringDocumentSource(helperOntologyString)

        val loaderConfiguration = OWLOntologyLoaderConfiguration()
        loaderConfiguration.isLoadAnnotationAxioms = false
        loaderConfiguration.isStrict = true
        loaderConfiguration.setReportStackTraces(true)
        loaderConfiguration.missingImportHandlingStrategy = MissingImportHandlingStrategy.THROW_EXCEPTION
        loaderConfiguration.setRepairIllegalPunnings(false)

        val axiom = try {
            val parsingOntology = knowledgeBase.ontology.owlOntologyManager.createOntology()
            try {
                parser.parse(docSource, parsingOntology, loaderConfiguration)

                parsingOntology
                    .axioms()
                    .asSequence()
                    .singleOrNull()
                    .let {
                        if (it == null) {
                            logger.error("A single axiom must be passed.")
                            return@parseAxiomExpression null
                        }

                        it
                    }
            } finally {
                knowledgeBase.ontology.owlOntologyManager.removeOntology(parsingOntology)
            }
        } catch (e: ParserException) {
            val message = e.message
            if (message != null) {
                logger.log(message)
                logger.log("")
            }
            logger.error("Could not parse functional axiom expression.")
            return null
        }

        return axiom
    }

    fun parseClassExpression(manchesterClassExpression: String): OWLClassExpression? {
        val parser = buildManchesterParser()

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

    // While the manchester syntax is nice for class expressions, it seems to fall short for stating facts about
    // individuals. In particular there does not seem to be much documentation on the syntax for isolated axiom
    // definitions.
    // Looking at the parser, it might not even be possible to write isolated axioms in manchester syntax besides
    // class assertions.
    // FIXME: Is this true?
    //
    // Hence, we use functional syntax for axiom expressions.
    // This is not supported either, but we can simulate it.
    fun isEntailed(functionalAxiomExpression: String, explain: Boolean = false): Boolean? {
        val axiom = parseAxiomExpression(functionalAxiomExpression) ?: return null

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