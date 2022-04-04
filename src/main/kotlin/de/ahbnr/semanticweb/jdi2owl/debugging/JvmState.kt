package de.ahbnr.semanticweb.jdi2owl.debugging

import com.sun.jdi.Location
import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference

/**
 * Later, we might want to implement an abstract interface over the JVM state here
 * so that the actual JDI source (reference JDI / IntelliJ Idea JDI / ...) is hidden....
 */

class JvmState(
    val pausedThread: ThreadReference,
    val location: Location
) {
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
        for (obj in pausedThread.virtualMachine().allClasses().asSequence().flatMap { it.instances(Long.MAX_VALUE) }) {
            if (obj.uniqueID() == objectId) {
                return obj
            }
        }

        return null
    }
}