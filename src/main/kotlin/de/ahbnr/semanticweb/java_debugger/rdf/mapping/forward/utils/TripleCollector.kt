package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.util.iterator.ExtendedIterator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TripleCollector(private val triplePattern: Triple) : KoinComponent {
    private val URIs: OntURIs by inject()

    private val collectedTriples = mutableSetOf<Triple>()

    sealed class CollectionObject {
        data class RDFList(val objects: List<Node>) : CollectionObject() {
            companion object {
                fun fromURIs(objects: List<String>) =
                    RDFList(objects.map { NodeFactory.createURI(it) })
            }
        }

        data class OWLUnion(val objects: List<Node>) : CollectionObject() {
            companion object {
                fun fromURIs(objects: List<String>) =
                    OWLUnion(objects.map { NodeFactory.createURI(it) })
            }
        }

        data class OWLOneOf(val objects: List<Node>) : CollectionObject() {
            companion object {
                fun fromURIs(objects: List<String>) =
                    OWLOneOf(objects.map { NodeFactory.createURI(it) })
            }
        }

        data class OWLSome(val propertyURI: String, val some: Node) : CollectionObject()

        sealed class CardinalityType {
            data class Exactly(val value: Int) : CardinalityType()
        }

        data class OWLCardinalityRestriction(
            val onPropertyUri: String,
            val onClassUri: String,
            val cardinality: CardinalityType
        ) : CollectionObject()
    }

    fun addStatement(subject: Node, predicate: Node, `object`: Node) {
        val candidateTriple = Triple(subject, predicate, `object`)

        if (triplePattern.matches(candidateTriple)) {
            collectedTriples.add(candidateTriple)
        }
    }

    fun addStatement(subject: Node, predicate: String, `object`: Node) {
        addStatement(
            subject,
            NodeFactory.createURI(predicate),
            `object`
        )
    }

    fun addStatement(subject: String, predicate: String, `object`: Node) {
        addStatement(
            NodeFactory.createURI(subject),
            NodeFactory.createURI(predicate),
            `object`
        )
    }

    fun addStatement(subject: Node, predicate: String, obj: String) {
        addStatement(
            subject,
            NodeFactory.createURI(predicate),
            NodeFactory.createURI(obj)
        )
    }

    fun addStatement(subject: String, predicate: String, obj: String) {
        addStatement(
            subject,
            predicate,
            NodeFactory.createURI(obj)
        )
    }

    fun addCollection(collectionObject: CollectionObject): Node =
        when (collectionObject) {
            is CollectionObject.RDFList -> addListStatements(
                collectionObject.objects
            )
            is CollectionObject.OWLUnion -> addUnionStatements(
                collectionObject.objects
            )
            is CollectionObject.OWLOneOf -> addOneOfStatements(
                collectionObject.objects
            )
            is CollectionObject.OWLSome -> addSomeRestriction(collectionObject.propertyURI, collectionObject.some)
            is CollectionObject.OWLCardinalityRestriction -> addCardinalityRestriction(collectionObject)
        }

    private fun addCardinalityRestriction(cardinalityRestriction: CollectionObject.OWLCardinalityRestriction): Node {
        val restrictionNode = NodeFactory.createBlankNode()

        addStatement(
            restrictionNode,
            URIs.rdf.type,
            URIs.owl.Restriction
        )

        addStatement(
            restrictionNode,
            URIs.owl.onProperty,
            cardinalityRestriction.onPropertyUri
        )

        addStatement(
            restrictionNode,
            URIs.owl.onClass,
            cardinalityRestriction.onClassUri
        )

        when (cardinalityRestriction.cardinality) {
            is CollectionObject.CardinalityType.Exactly ->
                addStatement(
                    restrictionNode,
                    URIs.owl.cardinality,
                    NodeFactory.createLiteral(
                        cardinalityRestriction.cardinality.value.toString(),
                        XSDDatatype.XSDnonNegativeInteger
                    )
                )
        }

        return restrictionNode
    }

    private fun addSomeRestriction(property: String, some: Node): Node {
        val restrictionNode = NodeFactory.createBlankNode()

        addStatement(
            restrictionNode,
            URIs.rdf.type,
            URIs.owl.Restriction
        )

        addStatement(
            restrictionNode,
            URIs.owl.onProperty,
            property
        )

        addStatement(
            restrictionNode,
            URIs.owl.someValuesFrom,
            some
        )

        return restrictionNode
    }

    private fun addOneOfStatements(objectList: List<Node>): Node {
        val oneOfNode = NodeFactory.createBlankNode()

        addStatement(
            oneOfNode,
            NodeFactory.createURI(URIs.owl.oneOf),
            addListStatements(objectList)
        )

        // Protege will not recognize the oneOf relation if we dont add this "is a class" declaration
        addStatement(
            oneOfNode,
            URIs.rdf.type,
            URIs.owl.Class
        )

        return oneOfNode
    }

    private fun addUnionStatements(objectList: List<Node>): Node {
        val unionNode = NodeFactory.createBlankNode()

        addStatement(
            unionNode,
            URIs.rdf.type,
            URIs.owl.Class
        )

        addStatement(
            unionNode,
            NodeFactory.createURI(URIs.owl.unionOf),
            addListStatements(objectList)
        )

        return unionNode
    }

    private fun addListStatements(objectList: List<Node>): Node {
        val firstObject = objectList.firstOrNull()

        return if (firstObject == null) {
            val root = NodeFactory.createURI(URIs.rdf.nil)

            root
        } else {
            val root = NodeFactory.createBlankNode()
            for (candidateTriple in genList(root, firstObject, objectList.drop(1))) {
                if (triplePattern.matches(candidateTriple)) {
                    collectedTriples.add(candidateTriple)
                }
            }

            root
        }
    }

    private fun genList(root: Node, first: Node, rest: List<Node>): Sequence<Triple> = sequence {
        yield(Triple(root, NodeFactory.createURI(URIs.rdf.type), NodeFactory.createURI(URIs.rdf.List)))
        yield(Triple(root, NodeFactory.createURI(URIs.rdf.first), first))

        val firstOfRest = rest.firstOrNull()
        if (firstOfRest == null) {
            yield(Triple(root, NodeFactory.createURI(URIs.rdf.rest), NodeFactory.createURI(URIs.rdf.nil)))
        } else {
            val restRoot = NodeFactory.createBlankNode()
            yield(Triple(root, NodeFactory.createURI(URIs.rdf.rest), restRoot))
            yieldAll(genList(restRoot, firstOfRest, rest.drop(1)))
        }
    }

    fun buildIterator(): ExtendedIterator<Triple> = TripleIterableIterator(collectedTriples)
}