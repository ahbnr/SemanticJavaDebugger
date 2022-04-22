package de.ahbnr.semanticweb.sjdb.repl.commands.utils

import de.ahbnr.semanticweb.jdi2owl.debugging.JvmState
import de.ahbnr.semanticweb.jdi2owl.linting.LinterMode
import de.ahbnr.semanticweb.jdi2owl.mapping.MappingLimiter
import de.ahbnr.semanticweb.jdi2owl.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.BuildParameters
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.MappingWithPlugins
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.TypeInfoProvider
import de.ahbnr.semanticweb.sjdb.mapping.forward.extensions.sourceinfo.SourceInfoPlugin
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import de.ahbnr.semanticweb.sjdb.repl.states.SemanticDebuggerState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spoon.Launcher
import spoon.compiler.ModelBuildingException
import spoon.reflect.CtModel
import kotlin.io.path.absolutePathString

class KnowledgeBaseBuilder(
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
            try {
                spoonLauncher.buildModel()
            } catch (e: ModelBuildingException) {
                if (sourcePath != null)
                    logger.error("Failed to build source model at source path ${sourcePath.absolutePathString()}.")
                throw e
            }
            if (!quiet && sourcePath != null)
                logger.success("Source model created.")
        }

        return spoonLauncher.model
    }

    fun buildGraphGenerator(): GraphGenerator =
        GraphGenerator(
            MappingWithPlugins(
                listOf(SourceInfoPlugin(buildSourceModel()))
            )
        )

    fun build(): KnowledgeBase? {
        logger.debug("Building knowledge base...")
        val limiter = MappingLimiter(debuggerState.mappingSettings)
        val graphGen = buildGraphGenerator()

        val buildParameters = BuildParameters(
            jvmState = jvmState,
            limiter = limiter,
            typeInfoProvider = TypeInfoProvider(jvmState.pausedThread)
        )
        val ontology = graphGen.buildOntology(
            buildParameters,
            debuggerState.applicationDomainDefFile,
            linterMode
        ).ontology ?: return null

        return KnowledgeBase(ontology, buildParameters)
    }
}
