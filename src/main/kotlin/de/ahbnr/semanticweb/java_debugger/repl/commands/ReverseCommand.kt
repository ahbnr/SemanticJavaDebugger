@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.sun.jdi.ObjectReference
import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.Namespaces
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.backward.BackwardMapper
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReverseCommand(
    val jvmDebugger: JvmDebugger,
    val ns: Namespaces
) : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "reverse"

    private val usage = """
        Usage: reverse <variable or IRI>
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val variableOrUri = argv.firstOrNull()
        if (variableOrUri == null) {
            logger.error(usage)
            return false
        }

        val jvm = jvmDebugger.jvm
        if (jvm == null) {
            logger.error("No JVM is running.")
            return false
        }

        val jvmState = jvm.state
        if (jvmState == null) {
            logger.error("JVM is currently not paused.")
            return false
        }

        val knowledgeBase = repl.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("You must first extract a knowledge base. Run buildkb.")
            return false
        }

        val model = knowledgeBase.ontology.asGraphModel()
        val node = knowledgeBase.resolveVariableOrUri(variableOrUri)

        if (node == null) {
            logger.error("No such node is known.")
            return false
        }

        val inverseMapping = BackwardMapper(ns, jvmState)

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

        return true
    }
}