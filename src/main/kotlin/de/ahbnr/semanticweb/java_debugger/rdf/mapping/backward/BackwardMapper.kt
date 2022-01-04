package de.ahbnr.semanticweb.java_debugger.rdf.mapping.backward

import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.Namespaces
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.backward.mappers.ObjectMapping
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.RDFNode

class BackwardMapper(
    private val ns: Namespaces,
    private val jvmState: JvmState
) {
    fun map(node: RDFNode, model: Model, limiter: MappingLimiter): Any? =
        ObjectMapping.map(node, model, ns, jvmState, limiter)
}