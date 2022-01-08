package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import com.sun.jdi.ClassType

fun ClassType.implementsInterface(fullyQualifiedName: String): Boolean =
    this.allInterfaces().any { it.name() == fullyQualifiedName }

fun ClassType.extendsClass(fullyQualifiedName: String): Boolean =
    this.name() == fullyQualifiedName || this.superclass()?.extendsClass(fullyQualifiedName) == true