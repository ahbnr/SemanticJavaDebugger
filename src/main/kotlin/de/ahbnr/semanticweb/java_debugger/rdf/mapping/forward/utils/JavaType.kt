package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import com.sun.jdi.Type

sealed class JavaType {
    data class LoadedType(val type: Type) : JavaType()
    data class UnloadedType(val typeName: String) : JavaType()
}