@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BuildKBCommand(
    val jvmDebugger: JvmDebugger,
    val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
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

        val limiter = MappingLimiter(
            excludedPackages = if (argv.contains("--limit-sdk"))
                setOf(
                    "sun",
                    "jdk",
                    "java.util.concurrent",
                    "java.security",
                    "java.lang.reflect",
                    "java.lang.ref",
                    "java.lang.module",
                    "java.lang.invoke",
                )
            else setOf(),
            shallowPackages = setOf("java")
        )

        val ontology = graphGenerator.buildOntology(state, repl.applicationDomainDefFile, limiter)
        repl.knowledgeBase = ontology

        logger.success("Knowledge base created.")
    }
}