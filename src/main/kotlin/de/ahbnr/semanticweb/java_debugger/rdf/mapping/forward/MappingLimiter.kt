package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import com.sun.jdi.*

data class MappingLimiter(
    val excludedPackages: Set<String>,
    val shallowPackages: Set<String>,
    val deepPackages: Set<String>,
    val reachableOnly: Boolean
) {
    private fun isExcluded(referenceType: ReferenceType) =
        excludedPackages.any { referenceType.name().startsWith(it) }

    private fun isShallow(referenceType: ReferenceType) =
        isExcluded(referenceType) || shallowPackages.any { referenceType.name().startsWith(it) }

    fun isDeep(referenceType: ReferenceType) =
        deepPackages.any { referenceType.name().startsWith(it) }

    fun canReferenceTypeBeSkipped(unloadedTypeName: String) =
        excludedPackages.any { unloadedTypeName.startsWith(it) }

    fun canReferenceTypeBeSkipped(referenceType: ReferenceType): Boolean {
        if (
            isExcluded(referenceType)
        ) {
            return true
        }

        if (
            isShallow(referenceType)
        ) {
            if (referenceType !is ArrayType && !referenceType.isPublic)
                return true

            if (referenceType is ArrayType) {
                val isComponentTypeSkippable = try {
                    val componentType = referenceType.componentType()

                    componentType is ReferenceType && canReferenceTypeBeSkipped(componentType)
                } catch (e: ClassNotLoadedException) {
                    canReferenceTypeBeSkipped(referenceType.componentTypeName())
                }

                return isComponentTypeSkippable
            }
        }

        return false
    }

    fun canMethodBeSkipped(method: Method): Boolean {
        val referenceType = method.declaringType()

        return canReferenceTypeBeSkipped(referenceType) || isShallow(referenceType) && !method.isPublic
    }

    fun canMethodDetailsBeSkipped(method: Method): Boolean {
        val referenceType = method.declaringType()

        return isShallow(referenceType)
    }

    fun canFieldBeSkipped(field: Field): Boolean {
        val referenceType = field.declaringType()

        return canReferenceTypeBeSkipped(referenceType) || isShallow(referenceType) && !field.isPublic
    }
}