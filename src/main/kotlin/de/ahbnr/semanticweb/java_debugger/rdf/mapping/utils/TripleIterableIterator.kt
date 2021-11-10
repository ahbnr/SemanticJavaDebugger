package de.ahbnr.semanticweb.java_debugger.rdf.mapping.utils

import org.apache.jena.graph.Triple
import org.apache.jena.util.iterator.NiceIterator

class TripleIterableIterator(collection: Iterable<Triple>): NiceIterator<Triple>() {
    private val current = collection.iterator()

    override fun next(): Triple = current.next()
    override fun hasNext(): Boolean = current.hasNext()
}