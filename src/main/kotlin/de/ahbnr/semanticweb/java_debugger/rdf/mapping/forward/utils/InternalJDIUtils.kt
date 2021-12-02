@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.tools.jdi.LocalVariableImpl
import java.lang.reflect.Field

object InternalJDIUtils {
    private val scopeStartField: Field = LocalVariableImpl::class.java.getDeclaredField("scopeStart")
    private val scopeEndField: Field = LocalVariableImpl::class.java.getDeclaredField("scopeEnd")
    private val slotField: Field = LocalVariableImpl::class.java.getDeclaredField("slot")

    init {
        scopeStartField.isAccessible = true
        scopeEndField.isAccessible = true
        slotField.isAccessible = true
    }

    fun getSlot(variable: LocalVariable) =
        slotField.get(variable) as Int

    fun getScopeStart(variable: LocalVariable) =
        scopeStartField.get(variable) as Location

    fun getScopeEnd(variable: LocalVariable) =
        scopeEndField.get(variable) as Location
}

