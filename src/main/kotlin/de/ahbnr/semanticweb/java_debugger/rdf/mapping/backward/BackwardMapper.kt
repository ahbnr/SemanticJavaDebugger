package de.ahbnr.semanticweb.java_debugger.rdf.mapping.backward

import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.backward.mappers.ObjectMapping
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter
import de.ahbnr.semanticweb.java_debugger.repl.KnowledgeBase
import org.apache.jena.rdf.model.RDFNode

class BackwardMapper(
    private val jvmState: JvmState
) {
    fun map(node: RDFNode, knowledgeBase: KnowledgeBase, limiter: MappingLimiter): Any? =
        ObjectMapping.map(node, knowledgeBase, jvmState, limiter)
}