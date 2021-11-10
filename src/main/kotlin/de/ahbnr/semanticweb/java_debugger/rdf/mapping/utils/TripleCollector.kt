package de.ahbnr.semanticweb.java_debugger.rdf.mapping.utils

import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.util.iterator.ExtendedIterator

class TripleCollector(private val triplePattern: Triple) {
    private val collectedTriples = mutableListOf<Triple>()

    fun addStatement(subject: String, predicate: String, obj: String) {
        val candidateTriple = Triple(
            NodeFactory.createURI(subject),
            NodeFactory.createURI(predicate),
            NodeFactory.createURI(obj)
        )

        if (triplePattern.matches(candidateTriple)) {
            collectedTriples.add(candidateTriple)
        }
    }

    fun buildIterator(): ExtendedIterator<Triple> = TripleIterableIterator(collectedTriples)
}