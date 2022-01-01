package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.debugging.ReferenceContexts
import de.ahbnr.semanticweb.java_debugger.debugging.utils.getFullyQualifiedName
import de.ahbnr.semanticweb.java_debugger.debugging.utils.getFullyQualifiedNamePrefix

data class MappingLimiter(
    private val excludedPackages: Set<String>,
    private val shallowPackages: Set<String>,
    private val deepFieldsAndVariables: Set<String>
) {
    fun isLimiting(): Boolean =
        excludedPackages.isNotEmpty() &&
                shallowPackages.isNotEmpty()

    private fun isExcluded(referenceType: ReferenceType) =
        excludedPackages.any { referenceType.name().startsWith(it) }

    private fun isShallow(referenceType: ReferenceType) =
        isExcluded(referenceType) || shallowPackages.any { referenceType.name().startsWith(it) }

    private fun isDeep(fullyQualifiedFieldOrVariableName: String) =
        deepFieldsAndVariables.any {
            fullyQualifiedFieldOrVariableName.startsWith(it)
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
        val namesOfReferencingVars = referenceContexts
            ?.getStackReferences(containerRef)
            ?.asSequence()
            ?.map { getFullyQualifiedNamePrefix(it.method, it.variable) }
        if (namesOfReferencingVars?.any { isDeep(it) } == true) {
            return false
        }

        val namesOfReferencingFields = referenceContexts
            ?.getReferencingFields(containerRef)
            ?.asSequence()
            ?.map { field -> getFullyQualifiedName(field) }

        if (namesOfReferencingFields?.any { isDeep(it) } == true) {
            return false
        }

        return true
    }
}