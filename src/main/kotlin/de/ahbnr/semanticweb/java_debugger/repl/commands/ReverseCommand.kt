@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.sun.jdi.ArrayReference
import com.sun.jdi.ArrayType
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.backward.BackwardMapper
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReverseCommand(
    val jvmDebugger: JvmDebugger
) : REPLCommand(name = "reverse"), KoinComponent {
    val logger: Logger by inject()
    val URIs: OntURIs by inject()

    val variableOrIRI: String by argument()

    override fun run() {
        val jvm = jvmDebugger.jvm
        if (jvm == null) {
            logger.error("No JVM is running.")
            throw ProgramResult(-1)
        }

        val jvmState = jvm.state
        if (jvmState == null) {
            logger.error("JVM is currently not paused.")
            throw ProgramResult(-1)
        }

        val knowledgeBase = state.tryGetKnowledgeBase()

        val model = knowledgeBase.ontology.asGraphModel()
        val node = knowledgeBase.resolveVariableOrUri(variableOrIRI)

        if (node == null) {
            logger.error("No such node is known.")
            throw ProgramResult(-1)
        }

        val inverseMapping = BackwardMapper(URIs.ns, jvmState)

        // Need to obtain the toString method from java.lang.Object
        // because array reference types do not return any methods
        val toStringMethod =
            jvm.vm.allClasses()
                .find { it.name() == "java.lang.Object" }
                ?.methodsByName("toString")
                ?.firstOrNull()
        if (toStringMethod == null) {
            logger.error("Can not obtain toString() method reference. This should never happen.")
        }

        when (val mapping = inverseMapping.map(node, model, knowledgeBase.buildParameters.limiter)) {
            is ObjectReference -> {
                logger.log("Java Object: $mapping")

                for ((field, value) in mapping.getValues(mapping.referenceType().allFields())) {
                    if (!field.isStatic) {
                        logger.log("  ${field.name()} = $value")
                    }
                }

                if (mapping is ArrayReference) {
                    val values = mapping
                        .values
                        .joinToString(", ") { it?.toString() ?: "null" }

                    logger.log("")
                    logger.log("  Array contents: [${values}]")

                    val doesArrayStoreReferences =
                        (mapping.referenceType() as? ArrayType)?.componentType() is ReferenceType
                    if (doesArrayStoreReferences) {
                        val strings = mapping
                            .values
                            .joinToString(", ") {
                                if (it != null) {
                                    (it as ObjectReference).invokeMethod(
                                        jvmState.pausedThread,
                                        toStringMethod,
                                        emptyList(),
                                        0
                                    ).toString()
                                } else "null"
                            }
                        logger.log("  As strings: [${strings}]")
                    }
                } else if (toStringMethod != null) {
                    // Be aware, that this invalidates any frame references for the paused thread
                    // and that they have to be retrieved again via frame(i)
                    val stringRepresentation = mapping.invokeMethod(
                        jvmState.pausedThread,
                        toStringMethod,
                        emptyList(),
                        0
                    )

                    logger.log("")
                    logger.log("  toString(): ${stringRepresentation}")
                }
            }
            else -> logger.error("Could not retrieve a Java construct for the given variable.")
        }

        logger.log("")
    }
}