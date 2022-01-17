package de.ahbnr.semanticweb.java_debugger.repl.commands.utils

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.optimization.extractSyntacticLocalityModule
import de.ahbnr.semanticweb.java_debugger.repl.KnowledgeBase
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

    fun close(classUri: String) {
        val classIri = knowledgeBase.resolvePrefixNameInUri(classUri)
        val `class` = knowledgeBase.ontology
            .classesInSignature()
            .asSequence()
            .find { it.iri.iriString == classIri }

        if (`class` == null) {
            if (!quiet)
                logger.error("There is no such class declared.")
            throw IllegalArgumentException("There is no such class declared.")
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
                if (doSyntacticExtraction)
                    extractSyntacticLocalityModule(
                        knowledgeBase,
                        `class`.signature().asSequence().toSet(),
                        classRelationDepth
                    )
                else knowledgeBase.ontology

            knowledgeBase
                .getOwlClassExpressionReasoner(ontology)
                .getInstances(`class`)
                .flatMap { it.entities().asSequence() }
                .toList()
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
                    "No individuals found. Hence, $classUri is now closed to contain no individuals."
                } else {
                    "Closed $classUri to be restricted to the individuals ${
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