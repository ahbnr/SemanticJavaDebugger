package de.ahbnr.semanticweb.jdi2owl.mapping.forward

import de.ahbnr.semanticweb.jdi2owl.debugging.JvmState
import de.ahbnr.semanticweb.jdi2owl.mapping.MappingLimiter
import spoon.reflect.CtModel

data class BuildParameters(
    val jvmState: JvmState,
    val sourceModel: CtModel,
    val limiter: MappingLimiter
)
