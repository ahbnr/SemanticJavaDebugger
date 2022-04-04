package de.ahbnr.semanticweb.jdi2owl.debugging.utils

import com.sun.jdi.Field
import com.sun.jdi.LocalVariable
import com.sun.jdi.Method
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.LocalVariableInfo

fun getFullyQualifiedName(field: Field): String =
    "${field.declaringType().name()}.${field.name()}"

fun getLocalMethodId(method: Method): String =
    "${method.returnTypeName()}_${method.name()}(${method.argumentTypeNames().joinToString(",")})"

fun getFullyQualifiedName(method: Method): String =
    "${method.declaringType().name()}.${getLocalMethodId(method)}"

/**
 * Since multiple variables with the same name can exist in a method, we can not
 * derive a unique name without static information, but at least a prefix of the fully qualified name
 */
fun getFullyQualifiedNamePrefix(method: Method, variable: LocalVariable): String =
    "${getFullyQualifiedName(method)}.${variable.name()}"

fun getFullyQualifiedName(variableInfo: LocalVariableInfo): String =
    "${getFullyQualifiedName(variableInfo.methodInfo.jdiMethod)}.${variableInfo.id}"