package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import spoon.reflect.CtModel

data class BuildParameters(
    val jvmState: JvmState,
    val sourceModel: CtModel,
    val limiter: MappingLimiter
)
