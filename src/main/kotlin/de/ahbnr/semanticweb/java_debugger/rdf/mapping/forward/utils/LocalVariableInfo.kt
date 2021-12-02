package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import com.sun.jdi.LocalVariable
import com.sun.jdi.Method
import spoon.reflect.code.CtLocalVariable

data class LocalVariableInfo(
    val id: String,
    val jdiMirror: LocalVariable,
    val jdiMethod: Method,
    val sourceDefinition: CtLocalVariable<*>?
) {
    fun getLine(): Int? {
        val sourceInfo = sourceDefinition?.position?.line

        return if (sourceInfo == null) {
            val jdiInfo = InternalJDIUtils.getScopeStart(jdiMirror).lineNumber()

            if (jdiInfo >= 0) {
                println(
                    "FALLBACK of $id TO $jdiInfo which is at code index ${
                        InternalJDIUtils.getScopeStart(jdiMirror).codeIndex()
                    } and at slot ${InternalJDIUtils.getSlot(jdiMirror)}"
                )
                jdiInfo
            } else null
        } else sourceInfo
    }
}