package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.debugging.ReferenceContexts

data class MappingLimiter(
    private val excludedPackages: Set<String>,
    private val shallowPackages: Set<String>,
    private val deepFields: Set<String>
) {
    fun isLimiting(): Boolean =
        excludedPackages.isNotEmpty() &&
                shallowPackages.isNotEmpty()

    private fun isExcluded(referenceType: ReferenceType) =
        excludedPackages.any { referenceType.name().startsWith(it) }

    private fun isShallow(referenceType: ReferenceType) =
        isExcluded(referenceType) || shallowPackages.any { referenceType.name().startsWith(it) }

    private fun isDeep(fullyQualifiedFieldName: String) =
        deepFields.any {
            fullyQualifiedFieldName.startsWith(it)
        }

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

    fun canSequenceBeSkipped(
        containerRef: ObjectReference,
        referenceContexts: ReferenceContexts?
    ): Boolean {
        val isReferencedOnStack = referenceContexts?.isReferencedByStack(containerRef)
        if (isReferencedOnStack == true) {
            return false
        }

        val namesOfReferencingFields = referenceContexts
            ?.getReferencingFields(containerRef)
            ?.asSequence()
            ?.map { field -> "${field.declaringType().name()}.${field.name()}" }

        if (namesOfReferencingFields?.any { isDeep(it) } == true) {
            return false
        }

        return true
    }
}