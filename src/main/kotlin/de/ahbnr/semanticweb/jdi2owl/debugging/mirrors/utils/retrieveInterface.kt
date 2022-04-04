package de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils

import com.sun.jdi.ClassType
import com.sun.jdi.InterfaceType
import com.sun.jdi.ObjectReference

fun retrieveInterface(objectRef: ObjectReference, fullyQualifiedInterfaceName: String): InterfaceType {
    val referenceType = objectRef.referenceType()
    if (referenceType !is ClassType) {
        throw MirroringError("Can not retrieve interface type $fullyQualifiedInterfaceName for object ${objectRef.uniqueID()} since it is not of a class type.")
    }

    val maybeInterfaceType = referenceType.allInterfaces().find { it.name() == fullyQualifiedInterfaceName }
    if (maybeInterfaceType == null) {
        throw MirroringError("Can not retrieve interface type $fullyQualifiedInterfaceName for object ${objectRef.uniqueID()} because its type ${referenceType.name()} does not implement the interface.")
    }

    return maybeInterfaceType
}