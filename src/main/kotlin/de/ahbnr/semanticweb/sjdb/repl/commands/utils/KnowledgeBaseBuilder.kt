package de.ahbnr.semanticweb.sjdb.repl.commands.utils

import de.ahbnr.semanticweb.jdi2owl.debugging.JvmState
import de.ahbnr.semanticweb.jdi2owl.linting.LinterMode
import de.ahbnr.semanticweb.jdi2owl.mapping.MappingLimiter
import de.ahbnr.semanticweb.jdi2owl.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.BuildParameters
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.TypeInfoProvider
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import de.ahbnr.semanticweb.sjdb.repl.SemanticDebuggerState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spoon.Launcher
import spoon.reflect.CtModel
import kotlin.io.path.absolutePathString

class KnowledgeBaseBuilder(
    val graphGenerator: GraphGenerator,
    val jvmState: JvmState,
    val debuggerState: SemanticDebuggerState,
    val linterMode: LinterMode,
    val quiet: Boolean,
) : KoinComponent {
    private val logger: Logger by inject()

    private fun buildSourceModel(): CtModel {
        val spoonLauncher = Launcher()

        debuggerState.sourcePath.let { sourcePath ->
            if (sourcePath != null) {
                spoonLauncher.addInputResource(sourcePath.absolutePathString())
            } else if (!quiet) {
                logger.debug("No path to source. Can not augment knowledge base with source structure.")
            }
            spoonLauncher.buildModel()
            if (!quiet && sourcePath != null)
                logger.success("Source model created.")
        }

        return spoonLauncher.model
    }

    fun build(): KnowledgeBase? {
        val limiter = MappingLimiter(debuggerState.mappingSettings)
        val sourceModel = buildSourceModel()

        val buildParameters = BuildParameters(
            jvmState = jvmState,
            sourceModel = sourceModel,
            limiter = limiter,
            typeInfoProvider = TypeInfoProvider(jvmState.pausedThread)
        )
        val ontology = graphGenerator.buildOntology(
            buildParameters,
            debuggerState.applicationDomainDefFile,
            linterMode
        ) ?: return null

        return KnowledgeBase(ontology, buildParameters)
    }
}
