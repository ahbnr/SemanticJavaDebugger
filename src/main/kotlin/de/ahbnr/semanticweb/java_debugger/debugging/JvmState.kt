package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.*

/**
 * Later, we might want to implement an abstract interface over the JVM state here
 * so that the actual JDI source (reference JDI / IntelliJ Idea JDI / ...) is hidden....
 */

data class JvmState(
    val pausedThread: ThreadReference
) {
    /**
     * Utility function to iterate over all objects
     *
     * Implementation is a bit hacky (iterate over all classes, then iterate over all instances per class).
     * However, this is exactly how IntelliJ does it in their Memory view implementation.
     *
     * FIXME: Check if JDWP protocol allows some better way of accessing the heap
     */
    fun allObjects(): Sequence<ObjectReference> = sequence {
        val vm = pausedThread.virtualMachine()
        val allReferenceTypes = vm.allClasses()

        for (referenceType in allReferenceTypes) {
            val allInstances = referenceType.instances(Long.MAX_VALUE)

            yieldAll(allInstances)
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
        for (obj in allObjects()) {
            if (obj.uniqueID() == objectId) {
                return obj
            }
        }

        return null
    }
}