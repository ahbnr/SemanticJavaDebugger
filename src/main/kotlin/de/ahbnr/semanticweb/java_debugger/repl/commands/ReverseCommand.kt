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
        Usage: reverse <variable name>
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        val jvm = jvmDebugger.jvm
        if (jvm == null) {
            logger.error("No JVM is running.")
            return
        }

        val jvmState = jvm.state
        if (jvmState == null) {
            logger.error("JVM is currently not paused.")
            return
        }

        val ontology = repl.knowledgeBase
        if (ontology == null) {
            logger.error("You must first extract a knowledge base. Run buildkb.")
            return
        }

        val model = ontology.asGraphModel()

        val queryResults = repl.queryResult
        val resultVars = repl.queryResultVars
        if (queryResults == null || resultVars == null) {
            logger.error("You must first perform a successful query before you can inspect query results")
            return
        }

        if (queryResults.isEmpty()) {
            logger.error("There are no results to reverse.")
            return
        }

        val variableName = argv.firstOrNull()
        if (variableName == null) {
            logger.error(usage)
            return
        }

        if (!resultVars.contains(variableName)) {
            logger.error("The variable $variableName was not part of the query results.")
            return
        }

        for ((solutionIdx, solution) in queryResults.withIndex()) {
            logger.log("Solution #$solutionIdx:")

            val node = solution[variableName]
            val inverseMapping = BackwardMapper(ns, jvmState)

            val mapping = inverseMapping.map(node, model)
            when (mapping) {
                is ObjectReference -> {
                    logger.log("Java Object: $mapping")
                }
                else -> logger.error("Could not retrieve a Java construct for the given variable.")
            }

            logger.log("")
        }
    }
}