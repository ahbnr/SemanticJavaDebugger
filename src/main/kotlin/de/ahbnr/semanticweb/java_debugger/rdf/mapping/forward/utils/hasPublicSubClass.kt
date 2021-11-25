package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import com.sun.jdi.ClassType

fun hasPublicSubClass(classType: ClassType): Boolean =
    classType.isPublic || classType.subclasses().any { hasPublicSubClass(it) }