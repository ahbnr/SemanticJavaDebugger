package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import com.sun.jdi.LocalVariable
import de.ahbnr.semanticweb.java_debugger.debugging.utils.InternalJDIUtils
import spoon.reflect.code.CtLocalVariable

data class LocalVariableInfo(
    val id: String,
    val jdiLocalVariable: LocalVariable,
    val methodInfo: MethodInfo,
    val sourceDefinition: CtLocalVariable<*>?
) {
    fun getLine(): Int? {
        val sourceInfo = sourceDefinition?.position?.line

        return if (sourceInfo == null) {
            val jdiInfo = InternalJDIUtils.getScopeStart(jdiLocalVariable).lineNumber()

            if (jdiInfo >= 0)
                jdiInfo
            else null
        } else sourceInfo
    }
}