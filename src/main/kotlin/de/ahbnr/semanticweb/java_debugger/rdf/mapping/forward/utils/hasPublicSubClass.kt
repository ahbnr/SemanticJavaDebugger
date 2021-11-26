package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import com.sun.jdi.ClassType
import com.sun.jdi.InterfaceType

fun hasPublicSubClass(classType: ClassType): Boolean =
    classType.isPublic || classType.subclasses().any { hasPublicSubClass(it) }

fun hasPublicSubInterface(interfaceType: InterfaceType): Boolean =
    interfaceType.isPublic || interfaceType.subinterfaces().any { hasPublicSubInterface(it) }
