@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.BuildParameters
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter
import de.ahbnr.semanticweb.java_debugger.repl.KnowledgeBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spoon.Launcher
import kotlin.io.path.absolutePathString

class BuildKBCommand(
    val jvmDebugger: JvmDebugger,
    val graphGenerator: GraphGenerator
) : REPLCommand(name = "buildkb"), KoinComponent {
    val logger: Logger by inject()

    val limitSdk: Boolean by option().flag(default = false)
    val fullLintingReport: Boolean by option().flag(default = false)
    val deep: List<String> by option().multiple()

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

        val sourcePath = state.sourcePath

        val spoonLauncher = Launcher()

        if (sourcePath != null) {
            spoonLauncher.addInputResource(sourcePath.absolutePathString())
        }
        spoonLauncher.buildModel()
        logger.success("Source model created.")

        val sourceModel = spoonLauncher.model

        val limiter = MappingLimiter(
            excludedPackages = if (limitSdk)
                setOf(
                    "sun",
                    "jdk",
                    "java.util.concurrent",
                    "java.security",
                    "java.lang.reflect",
                    "java.lang.ref",
                    "java.lang.module",
                    "java.lang.invoke",
                    "java"
                )
            else setOf(),
            shallowPackages = emptySet(), // setOf("java"),
            deepPackages = deep.toSet(),
            reachableOnly = true
        )

        val buildParameters = BuildParameters(
            jvmState = jvmState,
            sourceModel = sourceModel,
            limiter = limiter
        )
        val ontology = graphGenerator.buildOntology(
            buildParameters,
            state.applicationDomainDefFile,
            fullLintingReport
        )
        if (ontology == null) {
            logger.error("Could not create knowledge base.")
            throw ProgramResult(-1)
        }

        state.knowledgeBase = KnowledgeBase(ontology, limiter)

        logger.success("Knowledge base created.")
    }
}