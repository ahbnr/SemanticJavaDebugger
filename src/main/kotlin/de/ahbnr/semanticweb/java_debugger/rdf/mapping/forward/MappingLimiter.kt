package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import com.sun.jdi.ReferenceType

data class MappingLimiter(
    val excludedPackages: Set<String>,
    val shallowPackages: Set<String>,
    val deepPackages: Set<String>,
    val reachableOnly: Boolean
) {
    fun isExcluded(referenceType: ReferenceType) =
        excludedPackages.any { referenceType.name().startsWith(it) }

    fun isShallow(referenceType: ReferenceType) =
        isExcluded(referenceType) || shallowPackages.any { referenceType.name().startsWith(it) }

    fun isDeep(referenceType: ReferenceType) =
        deepPackages.any { referenceType.name().startsWith(it) }
}