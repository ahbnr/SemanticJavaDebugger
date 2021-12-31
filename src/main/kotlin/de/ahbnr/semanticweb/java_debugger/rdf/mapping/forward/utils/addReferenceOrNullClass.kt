package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import org.apache.jena.graph.NodeFactory

fun addReferenceOrNullClass(referenceTypeURI: String, tripleCollector: TripleCollector, URIs: OntURIs) =
// every reference can either be an instance or null:
// fieldTypeSubject ∪ { java:null }
    // [ owl:unionOf ( fieldTypeSubject [ owl:oneOf ( java:null ) ] ) ] .
    tripleCollector.addCollection(
        TripleCollector.CollectionObject.OWLUnion(
            listOf(
                NodeFactory.createURI(referenceTypeURI),

                tripleCollector.addCollection(
                    TripleCollector.CollectionObject.OWLOneOf.fromURIs(listOf(URIs.java.`null`))
                )
            )
        )
    )
