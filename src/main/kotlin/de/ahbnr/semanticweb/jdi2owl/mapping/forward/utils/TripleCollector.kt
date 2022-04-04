package de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils

import de.ahbnr.semanticweb.jdi2owl.mapping.OntURIs
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

    sealed class BlankNodeConstruct {
        data class RDFList(val objects: List<Node>) : BlankNodeConstruct() {
            companion object {
                fun fromURIs(objects: List<String>) =
                    RDFList(objects.map { NodeFactory.createURI(it) })
            }
        }

        data class OWLUnion(val objects: List<Node>) : BlankNodeConstruct() {
            companion object {
                fun fromURIs(objects: List<String>) =
                    OWLUnion(objects.map { NodeFactory.createURI(it) })
            }
        }

        data class OWLOneOf(val objects: List<Node>) : BlankNodeConstruct() {
            companion object {
                fun fromURIs(objects: List<String>) =
                    OWLOneOf(objects.map { NodeFactory.createURI(it) })
            }
        }

        class OWLSome : BlankNodeConstruct {
            val property: Node
            val some: Node

            constructor(property: Node, some: Node) {
                this.property = property
                this.some = some
            }

            constructor(propertyUri: String, some: Node) {
                this.property = NodeFactory.createURI(propertyUri)
                this.some = some
            }
        }

        sealed class CardinalityType {
            data class Exactly(val value: Int) : CardinalityType()
        }

        data class OWLObjectCardinalityRestriction(
            val onPropertyUri: String,
            val onClassUri: String,
            val cardinality: CardinalityType
        ) : BlankNodeConstruct()

        data class OWLDataCardinalityRestriction(
            val onPropertyUri: String,
            val cardinality: CardinalityType
        ) : BlankNodeConstruct()

        class OWLInverseProperty(
            val basePropertyUri: String
        ) : BlankNodeConstruct()
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

    fun addConstruct(blankNodeConstruct: BlankNodeConstruct): Node =
        when (blankNodeConstruct) {
            is BlankNodeConstruct.RDFList -> addListStatements(
                blankNodeConstruct.objects
            )
            is BlankNodeConstruct.OWLUnion -> addUnionStatements(
                blankNodeConstruct.objects
            )
            is BlankNodeConstruct.OWLOneOf -> addOneOfStatements(
                blankNodeConstruct.objects
            )
            is BlankNodeConstruct.OWLSome -> addSomeRestriction(blankNodeConstruct)
            is BlankNodeConstruct.OWLObjectCardinalityRestriction -> addObjectCardinalityRestriction(blankNodeConstruct)
            is BlankNodeConstruct.OWLDataCardinalityRestriction -> addDataCardinalityRestriction(blankNodeConstruct)
            is BlankNodeConstruct.OWLInverseProperty -> addInverseProperty(blankNodeConstruct)
        }

    private fun addObjectCardinalityRestriction(cardinalityRestriction: BlankNodeConstruct.OWLObjectCardinalityRestriction): Node {
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
            is BlankNodeConstruct.CardinalityType.Exactly ->
                addStatement(
                    restrictionNode,
                    URIs.owl.cardinality, // FIXME: This should be owl:qualifiedCardinality shouldnt it?
                    NodeFactory.createLiteralByValue(
                        cardinalityRestriction.cardinality.value,
                        XSDDatatype.XSDnonNegativeInteger
                    )
                )
        }

        return restrictionNode
    }

    private fun addDataCardinalityRestriction(cardinalityRestriction: BlankNodeConstruct.OWLDataCardinalityRestriction): Node {
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

        when (cardinalityRestriction.cardinality) {
            is BlankNodeConstruct.CardinalityType.Exactly ->
                addStatement(
                    restrictionNode,
                    URIs.owl.cardinality, // FIXME: This should be owl:qualifiedCardinality shouldnt it?
                    NodeFactory.createLiteralByValue(
                        cardinalityRestriction.cardinality.value,
                        XSDDatatype.XSDnonNegativeInteger
                    )
                )
        }

        return restrictionNode
    }

    private fun addInverseProperty(inversePropertyConstruct: BlankNodeConstruct.OWLInverseProperty): Node {
        val blankNode = NodeFactory.createBlankNode()

        addStatement(
            blankNode,
            URIs.rdf.type,
            URIs.owl.ObjectProperty
        )

        addStatement(
            blankNode,
            URIs.owl.inverseOf,
            inversePropertyConstruct.basePropertyUri
        )

        return blankNode
    }

    private fun addSomeRestriction(owlSome: BlankNodeConstruct.OWLSome): Node {
        val restrictionNode = NodeFactory.createBlankNode()

        addStatement(
            restrictionNode,
            URIs.rdf.type,
            URIs.owl.Restriction
        )

        addStatement(
            restrictionNode,
            URIs.owl.onProperty,
            owlSome.property
        )

        addStatement(
            restrictionNode,
            URIs.owl.someValuesFrom,
            owlSome.some
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