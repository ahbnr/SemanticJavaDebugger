package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import com.sun.jdi.ReferenceType

data class MappingLimiter(
    val excludedPackages: Set<String>,
    val shallowPackages: Set<String>
) {
    fun isExcluded(referenceType: ReferenceType) =
        excludedPackages.any { referenceType.name().startsWith(it) }

    fun isShallow(referenceType: ReferenceType) =
        shallowPackages.any { referenceType.name().startsWith(it) }
}