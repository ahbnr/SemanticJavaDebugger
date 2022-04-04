package de.ahbnr.semanticweb.jdi2owl.debugging.mirrors

import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils.MirroringError
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils.retrieveInterface
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils.retrieveMethod

// Be aware, that invoking any method of this mirror invalidates any frame references for the thread
// and that they have to be retrieved again via frame(i)
class IterableMirror(
    private val iterableRef: ObjectReference,
    private val thread: ThreadReference
) {
    val java_lang_Iterable_iterator: Method

    init {
        val interfaceType = retrieveInterface(iterableRef, "java.lang.Iterable")
        java_lang_Iterable_iterator = retrieveMethod(interfaceType, "iterator")
    }

    fun iterator(): Iterator<ObjectReference?>? {
        val iterator = iterableRef.invokeMethod(
            thread,
            java_lang_Iterable_iterator,
            emptyList(),
            0
        )

        if (iterator == null) {
            return null
        }

        if (iterator !is ObjectReference) {
            throw MirroringError("iterator() method of java.lang.Iterable implementor did not return an object reference. This should never happen.")
        }

        return IteratorMirror(iterator, thread)
    }
}