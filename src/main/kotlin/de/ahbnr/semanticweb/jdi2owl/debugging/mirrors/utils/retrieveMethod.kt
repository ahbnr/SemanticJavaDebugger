package de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils

import com.sun.jdi.Method
import com.sun.jdi.ReferenceType

fun retrieveMethod(referenceType: ReferenceType, methodName: String): Method {
    val method = referenceType.methodsByName(methodName).firstOrNull()

    return method
        ?: throw MirroringError("${referenceType.name()} does not provide $methodName method. This should never happen.")
}
