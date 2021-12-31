package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.Field
import com.sun.jdi.ObjectReference

class ReferenceContexts {
    private val objectIdsToContexts = mutableMapOf<Long, MutableList<Context>>()

    fun addContext(ref: ObjectReference, context: Context) {
        val id = ref.uniqueID()
        val contextList = when (val existingList = objectIdsToContexts.getOrDefault(id, null)) {
            null -> {
                val newList = mutableListOf<Context>()
                objectIdsToContexts[id] = newList
                newList
            }
            else -> existingList
        }

        contextList.add(context)
    }

    fun isReferencedByStack(objectReference: ObjectReference) =
        objectIdsToContexts
            .getOrDefault(objectReference.uniqueID(), null)
            ?.contains(Context.ReferencedByStack) == true

    fun getReferencingFields(objectReference: ObjectReference): List<Field> =
        objectIdsToContexts
            .getOrDefault(objectReference.uniqueID(), null)
            ?.filterIsInstance<Context.ReferencedByField>()
            ?.map { it.field }
            ?: listOf()

    sealed class Context {
        object ReferencedByStack : Context()
        class ReferencedByField(val field: Field) : Context()
    }
}