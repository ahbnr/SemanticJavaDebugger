package de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils

import com.sun.jdi.ClassType
import com.sun.jdi.InterfaceType

fun transitiveSubClasses(classType: ClassType): Sequence<ClassType> =
    classType.subclasses().asSequence().flatMap { transitiveSubClasses(it) }

fun transitiveSubInterfaces(interfaceType: InterfaceType): Sequence<InterfaceType> =
    interfaceType.subinterfaces().asSequence().flatMap { transitiveSubInterfaces(it) }

fun hasPublicSubClass(classType: ClassType): Boolean =
    classType.isPublic || classType.subclasses().any { hasPublicSubClass(it) }

fun hasPublicSubInterface(interfaceType: InterfaceType): Boolean =
    interfaceType.isPublic || interfaceType.subinterfaces().any { hasPublicSubInterface(it) }
