package de.ahbnr.semanticweb.sjdb.repl.commands.utils

import de.ahbnr.semanticweb.jdi2owl.Logger
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.apibinding.OWLFunctionalSyntaxFactory
import org.semanticweb.owlapi.model.OWLNamedIndividual
import kotlin.streams.asSequence
import kotlin.streams.toList

class ClassCloser(
    private val knowledgeBase: KnowledgeBase,
    private val noReasoner: Boolean,
    private val doSyntacticExtraction: Boolean,
    private val classRelationDepth: Int,
    private val quiet: Boolean
) : KoinComponent {
    private val logger: Logger by inject()

    fun close(classIRI: String) {
        val resolvedClassIRI = knowledgeBase.resolvePrefixNameInUri(classIRI)
        val `class` = knowledgeBase.ontology
            .classesInSignature()
            .asSequence()
            .find { it.iri.iriString == resolvedClassIRI }

        if (`class` == null) {
            val errorMsg = "There is no such class declared: $classIRI (resolved: $resolvedClassIRI)."
            if (!quiet)
                logger.error(errorMsg)
            throw IllegalArgumentException(errorMsg)
        }

        val individuals = if (noReasoner)
            knowledgeBase.ontology
                .classAssertionAxioms(
                    `class`
                )
                .map { it.individual }
                .toList()
        else {
            val ontology =
                if (doSyntacticExtraction) {
                    val extractor = SyntacticLocalityModuleExtractor(knowledgeBase, classRelationDepth, quiet)

                    extractor.extractModule(
                        `class`.signature().asSequence().toSet()
                    )
                } else knowledgeBase.ontology

            knowledgeBase
                .getDefaultOWLReasoner(ontology)
                .use { reasoner ->
                    reasoner
                        .getInstances(`class`)
                        .flatMap { it.entities().asSequence() }
                        .toList()
                }
        }

        knowledgeBase.ontology.add(
            OWLFunctionalSyntaxFactory.EquivalentClasses(
                `class`,
                OWLFunctionalSyntaxFactory.ObjectOneOf(*individuals.toTypedArray())
            )
        )

        if (!quiet)
            logger.log(
                if (individuals.isEmpty()) {
                    "No individuals found. Hence, $classIRI is now closed to contain no individuals."
                } else {
                    "Closed $classIRI to be restricted to the individuals ${
                        individuals
                            .joinToString(", ") {
                                val iri = (it as? OWLNamedIndividual)?.iri?.iriString
                                if (iri != null)
                                    knowledgeBase.asPrefixNameUri(iri)
                                else
                                    it.toString()
                            }
                    }."
                }
            )
    }
}
