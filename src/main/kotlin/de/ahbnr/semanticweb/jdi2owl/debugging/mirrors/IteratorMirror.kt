package de.ahbnr.semanticweb.jdi2owl.debugging.mirrors

import com.sun.jdi.BooleanValue
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils.MirroringError
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils.retrieveInterface
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils.retrieveMethod

// Be aware, that invoking any method of this mirror invalidates any frame references for the thread
// and that they have to be retrieved again via frame(i)
class IteratorMirror(
    private val iteratorRef: ObjectReference,
    private val thread: ThreadReference
) : Iterator<ObjectReference?> {
    val java_util_Iterator_hasNext: Method
    val java_util_Iterator_next: Method

    init {
        val interfaceType = retrieveInterface(iteratorRef, "java.util.Iterator")
        java_util_Iterator_hasNext = retrieveMethod(interfaceType, "hasNext")
        java_util_Iterator_next = retrieveMethod(interfaceType, "next")
    }

    override fun hasNext(): Boolean {
        val hasNext = iteratorRef.invokeMethod(
            thread,
            java_util_Iterator_hasNext,
            emptyList(),
            0
        )

        if (hasNext is BooleanValue) {
            return hasNext.value()
        } else {
            throw MirroringError("hasNext() method of java.util.Iterator did not return a boolean. This should never happen.")
        }
    }

    override fun next(): ObjectReference? {
        val result = iteratorRef.invokeMethod(
            thread,
            java_util_Iterator_next,
            emptyList(),
            0
        )

        if (result !is ObjectReference?) {
            throw MirroringError("next() method of java.util.Iterator did not return an object reference (or null). This should never happen.")
        }

        return result
    }
}