package de.ahbnr.semanticweb.java_debugger.rdf.mapping.backward.mappers

import com.sun.jdi.ObjectReference
import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.Namespaces
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter
import org.apache.jena.rdf.model.Literal
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource

object ObjectMapping {
    private class MappingContext(
        val resource: Resource,
        val model: Model,
        val ns: Namespaces
    ) {
        fun isJavaObject(): Boolean =
            resource.hasProperty(
                model.getProperty(ns.rdf + "type"),
                model.getResource(ns.java + "Object")
            )

        fun getObjectId(): Long? =
            (
                    resource.getProperty(
                        model.getProperty(ns.java + "hasJDWPObjectId")
                    )
                        ?.`object` as? Literal
                    )
                ?.lexicalForm
                ?.toLong()
    }


    fun map(
        node: RDFNode,
        model: Model,
        ns: Namespaces,
        jvmState: JvmState,
        limiter: MappingLimiter
    ): ObjectReference? {
        val resource = node as? Resource ?: return null

        val context = MappingContext(resource, model, ns)
        if (!context.isJavaObject()) {
            return null
        }

        val objectId = context.getObjectId() ?: return null

        return jvmState.getObjectById(objectId, limiter)
    }
}