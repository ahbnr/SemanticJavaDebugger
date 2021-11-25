package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter

/**
 * Later, we might want to implement an abstract interface over the JVM state here
 * so that the actual JDI source (reference JDI / IntelliJ Idea JDI / ...) is hidden....
 */

data class JvmState(
    val pausedThread: ThreadReference
) {
    private val referencedByLocalVar = mutableSetOf<Long>()
    private val stackReferences = mutableListOf<ObjectReference>()

    init {
        for (frameDepth in 0 until pausedThread.frameCount()) {
            val frame = pausedThread.frame(0)

            for ((_, value) in frame.getValues(frame.visibleVariables())) {
                if (value is ObjectReference) {
                    referencedByLocalVar.add(value.uniqueID())
                    stackReferences.add(value)
                }
            }
        }
    }

    fun isReferencedByVariable(objectReference: ObjectReference) =
        referencedByLocalVar.contains(objectReference.uniqueID())

    private fun recursivelyFindObjects(
        objectReference: ObjectReference,
        limiter: MappingLimiter,
        seen: MutableSet<Long>
    ): Sequence<ObjectReference> = sequence {
        val id = objectReference.uniqueID()
        if (seen.contains(id))
            return@sequence
        seen.add(id)

        val referenceType = objectReference.referenceType()

        if (limiter.isExcluded(referenceType))
            return@sequence

        for (field in referenceType.fields()) { // FIXME: Also handle inherited fields
            if (field.isStatic)
                continue

            if (!field.isPublic && limiter.isShallow(referenceType))
                continue

            val value = objectReference.getValue(field)

            if (value !is ObjectReference)
                continue

            yieldAll(recursivelyFindObjects(value, limiter, seen))
        }

        yield(objectReference)
    }

    /**
     * Utility function to iterate over all objects
     *
     * Implementation is a bit hacky (iterate over all classes, then iterate over all instances per class).
     * However, this is exactly how IntelliJ does it in their Memory view implementation.
     *
     * FIXME: Check if JDWP protocol allows some better way of accessing the heap
     */
    fun allObjects(limiter: MappingLimiter?): Sequence<ObjectReference> = sequence {
        val vm = pausedThread.virtualMachine()
        val allReferenceTypes = vm.allClasses()

        if (limiter?.reachableOnly == true) {
            val seen = mutableSetOf<Long>()

            // get objects in variables
            for (value in stackReferences) {
                yieldAll(
                    recursivelyFindObjects(
                        value,
                        limiter,
                        seen
                    )
                )
            }

            // get objects directly referenced by static fields
            for (referenceType in allReferenceTypes) {
                if (limiter.isExcluded(referenceType))
                    continue

                for (field in referenceType.allFields()) {
                    if (!field.isStatic)
                        continue

                    if (!field.isPublic && limiter.isShallow(referenceType))
                        continue

                    val value = referenceType.getValue(field)
                    if (value !is ObjectReference)
                        continue

                    yieldAll(
                        recursivelyFindObjects(
                            value,
                            limiter,
                            seen
                        )
                    )
                }
            }
        } else {
            // This is how IntelliJ does it in its memory view.
            // However, IntelliJ limits the number of objects being retrieved.
            for (referenceType in allReferenceTypes) {
                val allInstances = referenceType.instances(Long.MAX_VALUE)

                yieldAll(allInstances)
            }
        }
    }

    /**
     * Utility function to retrieve an object using its ID.
     *
     * FIXME: Current implementation requires iteration over all instances,
     *   very inefficient.
     *   JDWP permits an efficient implementation, however, the JDI reference implementation from the JDK
     *   does not implement it in the public interface.
     *   ...
     *   There are multiple options, to fix this, see also my notes:
     *   ...
     *   1. Copy the OpenJDK or Eclipse or IntelliJ implementation of JDI and make internal factories for
     *      ObjectReferences publicly available
     *   2. Implement JDI myself
     *   3. Use illegal reflective accesses to access the internal factories of the JDK implementation
     *   4. Maintain a HashMap from ObjectIDs to ObjectReferences for any Jena Model
     *   5. Append the ObjectReferences to Apache Jena Nodes / Statements (is that possible?)
     */
    fun getObjectById(objectId: Long): ObjectReference? {
        for (obj in allObjects(null)) {
            if (obj.uniqueID() == objectId) {
                return obj
            }
        }

        return null
    }
}