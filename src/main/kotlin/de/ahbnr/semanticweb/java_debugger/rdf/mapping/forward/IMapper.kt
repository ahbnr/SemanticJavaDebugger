package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import org.apache.jena.rdf.model.Model

interface IMapper {
    fun extendModel(jvmState: JvmState, outputModel: Model, limiter: MappingLimiter)
}