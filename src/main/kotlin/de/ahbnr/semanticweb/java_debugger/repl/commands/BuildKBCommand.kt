@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.debugging.JVMDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.genDefaultNs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.mappers.ClassMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.mappers.ObjectMapper
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryParseException
import org.apache.jena.query.ResultSetFormatter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BuildKBCommand(
    val jvmDebugger: JVMDebugger,
    val graphGenerator: GraphGenerator
): IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "buildkb"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        val jvm = jvmDebugger.jvm
        if (jvm == null) {
            logger.error("No JVM is running.")
            return
        }

        val state = jvm.state
        if (state == null) {
            logger.error("JVM is currently not paused.")
            return
        }
        val ontology = graphGenerator.buildOntology(state.pausedThread, repl.applicationDomainDefFile)
        repl.knowledgeBase = ontology
    }
}