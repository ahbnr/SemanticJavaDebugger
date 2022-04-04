package de.ahbnr.semanticweb.sjdb.rdf.mapping.forward.utils

import com.sun.jdi.Type

sealed class JavaType {
    data class LoadedType(val type: Type) : JavaType()
    data class UnloadedType(val typeName: String) : JavaType()
}