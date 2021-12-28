@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.sun.jdi.ObjectReference
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

        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("You must first extract a knowledge base. Run buildkb.")
            throw ProgramResult(-1)
        }

        val model = knowledgeBase.ontology.asGraphModel()
        val node = knowledgeBase.resolveVariableOrUri(variableOrIRI)

        if (node == null) {
            logger.error("No such node is known.")
            throw ProgramResult(-1)
        }

        val inverseMapping = BackwardMapper(URIs.ns, jvmState)

        when (val mapping = inverseMapping.map(node, model)) {
            is ObjectReference -> {
                logger.log("Java Object: $mapping")
                for ((field, value) in mapping.getValues(mapping.referenceType().allFields())) {
                    logger.log("  ${field.name()} = $value")
                }
            }
            else -> logger.error("Could not retrieve a Java construct for the given variable.")
        }

        logger.log("")
    }
}