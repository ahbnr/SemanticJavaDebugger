@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package de.ahbnr.semanticweb.jdi2owl.debugging.utils

import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.jdi.VirtualMachine
import com.sun.tools.jdi.LocalVariableImpl
import com.sun.tools.jdi.ObjectReferenceImpl
import java.lang.reflect.Constructor
import java.lang.reflect.Field

object InternalJDIUtils {
    private val scopeStartField: Field = LocalVariableImpl::class.java.getDeclaredField("scopeStart")
    private val scopeEndField: Field = LocalVariableImpl::class.java.getDeclaredField("scopeEnd")
    private val slotField: Field = LocalVariableImpl::class.java.getDeclaredField("slot")
    private val objectReferenceImplConstructor: Constructor<ObjectReferenceImpl>

    init {
        scopeStartField.isAccessible = true
        scopeEndField.isAccessible = true
        slotField.isAccessible = true

        objectReferenceImplConstructor = ObjectReferenceImpl::class.java.declaredConstructors.find {
            it.parameterCount == 2 &&
                    it.parameterTypes[0].isAssignableFrom(VirtualMachine::class.java) &&
                    it.parameterTypes[1].canonicalName == "long"
        } as Constructor<ObjectReferenceImpl>?
            ?: throw RuntimeException("Could not retrieve internal JDI ObjectReferenceImpl constructor. Are you compiling with the default JDK 11?")

        objectReferenceImplConstructor.isAccessible = true
    }

    fun getSlot(variable: LocalVariable) =
        slotField.get(variable) as Int

    fun getScopeStart(variable: LocalVariable) =
        scopeStartField.get(variable) as Location

    fun getScopeEnd(variable: LocalVariable) =
        scopeEndField.get(variable) as Location

    fun objectReferenceFromId(vm: VirtualMachine, objectId: Long) =
        objectReferenceImplConstructor.newInstance(vm, objectId)
}

