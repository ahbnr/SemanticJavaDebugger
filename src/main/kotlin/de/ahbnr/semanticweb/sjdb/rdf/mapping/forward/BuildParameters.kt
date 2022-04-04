package de.ahbnr.semanticweb.sjdb.rdf.mapping.forward

import de.ahbnr.semanticweb.sjdb.debugging.JvmState
import spoon.reflect.CtModel

data class BuildParameters(
    val jvmState: JvmState,
    val sourceModel: CtModel,
    val limiter: MappingLimiter
)
