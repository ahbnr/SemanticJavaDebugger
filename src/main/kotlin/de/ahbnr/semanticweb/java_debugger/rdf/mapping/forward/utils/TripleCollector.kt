package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.util.iterator.ExtendedIterator

class TripleCollector(private val triplePattern: Triple) {
    private val collectedTriples = mutableListOf<Triple>()

    fun addStatement(subject: Node, predicate: Node, `object`: Node) {
        val candidateTriple = Triple(subject, predicate, `object`)

        if (triplePattern.matches(candidateTriple)) {
            collectedTriples.add(candidateTriple)
        }
    }

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