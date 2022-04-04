package de.ahbnr.semanticweb.jdi2owl.linting

import com.github.owlcs.ontapi.jena.model.OntObject
import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.OntURIs
import openllet.pellint.rdfxml.OWLEntityDatabase
import openllet.pellint.rdfxml.OWLSyntaxChecker
import openllet.pellint.rdfxml.RDFModel
import org.apache.jena.rdf.model.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.reflect.Field

/**
 * Wrapper of OWLSyntaxChecker which filters common lints that are not important in this
 * application.
 */
class FilteredOwlSyntaxChecker(
    private val model: Model,
    private val namespacesToFiler: Set<String>
) : KoinComponent {
    private val URIs: OntURIs by inject()

    private val annotationSourceProperty = model.getProperty(URIs.owl.annotatedSource)
    private val annotationTargetProperty = model.getProperty(URIs.owl.annotatedTarget)

    private val chainsPropertiesProperty = model.getProperty(URIs.macros.chainsProperties)

    private fun filterUntyped(toFilter: Set<RDFNode>): Set<RDFNode> =
        toFilter.filter {
            // by namespace
            (it !is OntObject || !namespacesToFiler.contains(it.nameSpace)) &&
                    (it !is Property || !namespacesToFiler.contains(it.nameSpace)) &&
                    // When annotating axioms, classes may be treated as individuals without declaring them as such:
                    // https://www.w3.org/2007/OWL/wiki/Quick_Reference_Guide#Annotations
                    // This is fine and should not trigger a warning
                    !model.contains(null, annotationTargetProperty, it) &&
                    !model.contains(null, annotationSourceProperty, it) &&
                    // The chainsProperties property has a rdf list (untyped b-node) on the right-hand side.
                    // This is fine:
                    !model.contains(null, chainsPropertiesProperty, it)
        }.toSet()

    companion object {
        private val excludeValidPunningsField: Field =
            OWLSyntaxChecker::class.java.getDeclaredField("_excludeValidPunnings")
        private val owlEntitiesField: Field = OWLSyntaxChecker::class.java.getDeclaredField("_OWLEntities")

        init {
            excludeValidPunningsField.isAccessible = true
            owlEntitiesField.isAccessible = true
        }
    }

    private val OWLSyntaxChecker.OWLEntities: OWLEntityDatabase
        @Suppress("UNCHECKED_CAST")
        get() = owlEntitiesField.get(this) as OWLEntityDatabase

    private val OWLSyntaxChecker.excludeValidPunnings: Boolean
        @Suppress("UNCHECKED_CAST")
        get() = excludeValidPunningsField.get(this) as Boolean

    fun validate(): OwlSyntaxLints {
        val owlSyntaxChecker = OWLSyntaxChecker()
        // do not report those punnings which are valid under OWL 2
        owlSyntaxChecker.isExcludeValidPunnings = true

        val rdfModel = RDFModel()
        rdfModel.add(model)
        owlSyntaxChecker.validate(rdfModel)

        return reportLints(owlSyntaxChecker)
    }

    // Based on internals of openllet.pellint.rdfxml.OWLSyntaxChecker
    private fun reportLints(checker: OWLSyntaxChecker): OwlSyntaxLints {
        val entities = checker.OWLEntities

        val lints = OwlSyntaxLints()
        lints.addNodes("Untyped ontologies", filterUntyped(entities.getDoubtfulOntologies()))
        lints.addNodes("Untyped classes", filterUntyped(entities.getDoubtfulClasses()))
        lints.addNodes("Untyped datatypes", filterUntyped(entities.getDoubtfulDatatypes()))
        lints.addNodes("Untyped object properties", filterUntyped(entities.getDoubtfulObjectRoles()))
        lints.addNodes("Untyped datatype properties", filterUntyped(entities.getDoubtfulDatatypeRoles()))
        lints.addNodes("Untyped annotation properties", filterUntyped(entities.getDoubtfulAnnotaionRoles()))
        lints.addNodes("Untyped properties", filterUntyped(entities.getDoubtfulRoles()))
        lints.addNodes("Untyped individuals", filterUntyped(entities.getDoubtfulIndividuals()))

        lints.addNodes("Using rdfs:Class instead of owl:Class", entities.getAllRDFClasses())
        lints.addMultiTypedResources(
            "Multiple typed resources",
            entities.getMultiTypedResources(checker.excludeValidPunnings)
        )
        lints.addLiterals(
            "Literals used where a class is _expected",
            entities.getLiteralsAsClass()
        )
        lints.addLiterals(
            "Literals used where an _individual is _expected",
            entities.getLiteralsAsIndividuals()
        )
        lints.addNodes(
            "Resource used where a literal is _expected",
            entities.getResourcesAsLiterals()
        )

        return lints
    }
}

class OwlSyntaxLints : KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    private sealed class Lint {
        class Node(val node: RDFNode) : Lint() {
            override fun render(): String = renderNode(node)
        }

        class MultiTypedResource(val node: RDFNode, val types: List<String>) : Lint() {
            override fun render(): String = "${renderNode(node)} has types ${types.joinToString(", ")}"
        }

        class Literal(val literal: org.apache.jena.rdf.model.Literal) : Lint() {
            override fun render(): String = "\"$literal\""
        }

        abstract fun render(): String

        protected fun renderNode(node: RDFNode): String =
            if (node.isAnon) {
                """
                        BNode with the following incoming relations:
                        ${
                    node
                        .model
                        .listStatements(SimpleSelector(null, null, node))
                        .mapWith { "  $it" }
                        .toList()
                        .joinToString("\n")
                }
                    """.trimIndent()
            } else node.toString()
    }

    private val lints = mutableMapOf<String, MutableSet<Lint>>()

    val onlyUntypedJavaObjects: Boolean
        get() =
            lints.isEmpty() || lints.size == 1 && lints.keys.contains("Untyped individuals") && lints["Untyped individuals"]!!.all {
                it is Lint.Node && it.node is Resource && it.node.isURIResource && URIs.run.isObjectURI(
                    it.node.uri
                )
            }

    fun isEmpty() = lints.isEmpty()

    fun addNodes(category: String, nodes: Set<RDFNode>) {
        val valueSet = lints.getOrDefault(category, mutableSetOf())
        valueSet.addAll(nodes.map { Lint.Node(it) })

        if (valueSet.isNotEmpty())
            lints[category] = valueSet
    }

    fun addLiterals(category: String, literals: Set<Literal>) {
        val valueSet = lints.getOrDefault(category, mutableSetOf())
        valueSet.addAll(literals.map { Lint.Literal(it) })

        if (valueSet.isNotEmpty())
            lints[category] = valueSet
    }

    fun addMultiTypedResources(category: String, resources: Map<RDFNode, List<String>>) {
        val valueSet = lints.getOrDefault(category, mutableSetOf())
        valueSet.addAll(resources.map { (resource, types) -> Lint.MultiTypedResource(resource, types) })

        if (valueSet.isNotEmpty())
            lints[category] = valueSet
    }

    fun log() {
        for ((category, lints) in lints.entries) {
            if (!lints.isEmpty()) {
                logger.log("[$category]")
                for (lint in lints) {
                    val line = "- ${lint.render()}"

                    val isUntypedJavaObject =
                        lint is Lint.Node && lint.node is Resource && lint.node.isURIResource && URIs.run.isObjectURI(
                            lint.node.uri
                        )
                    if (isUntypedJavaObject) {
                        logger.debug(line)
                    } else {
                        logger.log(line)
                    }
                }
            }
        }
    }
}