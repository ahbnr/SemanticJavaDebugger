@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.apibinding.OWLFunctionalSyntaxFactory.EquivalentClasses
import org.semanticweb.owlapi.apibinding.OWLFunctionalSyntaxFactory.ObjectOneOf
import org.semanticweb.owlapi.model.OWLNamedIndividual
import kotlin.streams.asSequence
import kotlin.streams.toList

class CloseClass : REPLCommand(name = "close"), KoinComponent {
    private val logger: Logger by inject()

    val owlClass: String by argument()

    val noReasoner by option().flag(default = false)

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        val classIri = knowledgeBase.resolvePrefixNameInUri(owlClass)
        val `class` = knowledgeBase.ontology
            .classesInSignature()
            .asSequence()
            .find { it.iri.iriString == classIri }

        if (`class` == null) {
            logger.error("There is no such class declared.")
            throw ProgramResult(-1)
        }

        val individuals = if (noReasoner)
            knowledgeBase.ontology
                .classAssertionAxioms(
                    `class`
                )
                .map { it.individual }
                .toList()
        else knowledgeBase
            .getOwlClassExpressionReasoner(knowledgeBase.ontology)
            .getInstances(`class`)
            .flatMap { it.entities().asSequence() }
            .toList()

        knowledgeBase.ontology.add(
            EquivalentClasses(
                `class`,
                ObjectOneOf(*individuals.toTypedArray())
            )
        )

        logger.log(
            if (individuals.isEmpty()) {
                "No individuals found. Hence, $owlClass is now closed to contain no individuals."
            } else {
                "Closed $owlClass to be restricted to the individuals ${
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