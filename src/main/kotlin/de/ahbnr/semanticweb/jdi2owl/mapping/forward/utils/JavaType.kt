package de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils

import com.sun.jdi.Type

sealed class JavaType {
    data class LoadedType(val type: Type) : JavaType()
    data class UnloadedType(val typeName: String) : JavaType()
}