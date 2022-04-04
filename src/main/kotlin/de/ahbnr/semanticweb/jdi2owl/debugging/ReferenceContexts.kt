package de.ahbnr.semanticweb.jdi2owl.debugging

import com.sun.jdi.Field
import com.sun.jdi.LocalVariable
import com.sun.jdi.Method
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

    fun getStackReferences(objectReference: ObjectReference): List<Context.ReferencedByStack> =
        objectIdsToContexts
            .getOrDefault(objectReference.uniqueID(), null)
            ?.filterIsInstance<Context.ReferencedByStack>()
            ?: emptyList()

    fun getReferencingFields(objectReference: ObjectReference): List<Field> =
        objectIdsToContexts
            .getOrDefault(objectReference.uniqueID(), null)
            ?.filterIsInstance<Context.ReferencedByField>()
            ?.map { it.field }
            ?: listOf()

    sealed class Context {
        class ReferencedByStack(val method: Method, val variable: LocalVariable) : Context()
        class ReferencedByField(val field: Field) : Context()
    }
}