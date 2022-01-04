package de.ahbnr.semanticweb.java_debugger.repl.commands.utils

import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.linting.LinterMode
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.BuildParameters
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter
import de.ahbnr.semanticweb.java_debugger.repl.KnowledgeBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spoon.Launcher
import spoon.reflect.CtModel
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class KnowledgeBaseBuilder(
    val graphGenerator: GraphGenerator,
    val sourcePath: Path?,
    val applicationDomainDefFile: String?,
    val jvmState: JvmState,
    val limitSdk: Boolean,
    val deepFieldsAndVariables: Set<String>,
    val linterMode: LinterMode,
    val quiet: Boolean,
) : KoinComponent {
    private val logger: Logger by inject()

    private fun buildSourceModel(): CtModel {
        val spoonLauncher = Launcher()

        if (sourcePath != null) {
            spoonLauncher.addInputResource(sourcePath.absolutePathString())
        } else if (!quiet) {
            logger.debug("No path to source. Can not augment knowledge base with source structure.")
        }
        spoonLauncher.buildModel()
        if (!quiet)
            logger.success("Source model created.")

        return spoonLauncher.model
    }

    private fun buildLimiter(): MappingLimiter =
        MappingLimiter(
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
                )
            else setOf(),
            shallowPackages = setOf("java"),
            deepFieldsAndVariables = deepFieldsAndVariables
        )

    fun build(): KnowledgeBase? {
        val limiter = buildLimiter()
        val sourceModel = buildSourceModel()

        val buildParameters = BuildParameters(
            jvmState = jvmState,
            sourceModel = sourceModel,
            limiter = limiter
        )
        val ontology = graphGenerator.buildOntology(
            buildParameters,
            applicationDomainDefFile,
            linterMode
        ) ?: return null

        return KnowledgeBase(ontology, buildParameters)
    }
}