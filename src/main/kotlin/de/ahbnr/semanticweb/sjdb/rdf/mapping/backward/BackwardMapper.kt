package de.ahbnr.semanticweb.sjdb.rdf.mapping.backward

import de.ahbnr.semanticweb.jdi2owl.debugging.JvmState
import de.ahbnr.semanticweb.sjdb.rdf.mapping.backward.mappers.ObjectMapping
import de.ahbnr.semanticweb.jdi2owl.mapping.MappingLimiter
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import org.apache.jena.rdf.model.RDFNode

class BackwardMapper(
    private val jvmState: JvmState
) {
    fun map(node: RDFNode, knowledgeBase: KnowledgeBase, limiter: MappingLimiter): Any? =
        ObjectMapping.map(node, knowledgeBase, jvmState, limiter)
}