@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.BuildParameters
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter
import de.ahbnr.semanticweb.java_debugger.repl.KnowledgeBase
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spoon.Launcher
import kotlin.io.path.absolutePathString

class BuildKBCommand(
    val jvmDebugger: JvmDebugger,
    val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "buildkb"
    private val usage = "$name [--limit-sdk] [--full-linting-report] [--deep=...]*"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val jvm = jvmDebugger.jvm
        if (jvm == null) {
            logger.error("No JVM is running.")
            return false
        }

        val state = jvm.state
        if (state == null) {
            logger.error("JVM is currently not paused.")
            return false
        }

        val sourcePath = repl.sourcePath

        val spoonLauncher = Launcher()

        if (sourcePath != null) {
            spoonLauncher.addInputResource(sourcePath.absolutePathString())
        }
        spoonLauncher.buildModel()
        logger.success("Source model created.")

        val sourceModel = spoonLauncher.model

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
            shallowPackages = setOf("java"),
            deepPackages = argv
                .filter { it.startsWith("--deep=") }
                .map { it.substring("--deep=".length until it.length) }
                .toSet(),
            reachableOnly = true
        )

        val buildParameters = BuildParameters(
            jvmState = state,
            sourceModel = sourceModel,
            limiter = limiter
        )
        val ontology = graphGenerator.buildOntology(
            buildParameters,
            repl.applicationDomainDefFile,
            argv.contains("--full-linting-report")
        )
        if (ontology == null) {
            logger.error("Could not create knowledge base.")
            return false
        }

        repl.knowledgeBase = KnowledgeBase(ontology, repl)

        logger.success("Knowledge base created.")

        return true
    }
}