@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import de.ahbnr.semanticweb.sjdb.rdf.linting.LinterMode
import de.ahbnr.semanticweb.sjdb.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.sjdb.repl.commands.utils.KnowledgeBaseBuilder
import org.koin.core.component.KoinComponent

class BuildKBCommand(
    val graphGenerator: GraphGenerator
) : REPLCommand(name = "buildkb"), KoinComponent {
    val linting: LinterMode by option().choice(
        "default" to LinterMode.Normal,
        "full" to LinterMode.FullReport,
        "none" to LinterMode.NoLinters
    ).default(LinterMode.Normal)

    override fun run() {
        val jvmState = tryGetJvmState()


        val builder = KnowledgeBaseBuilder(
            graphGenerator = graphGenerator,
            jvmState = jvmState,
            debuggerState = state,
            linterMode = linting,
            quiet = false
        )

        val newKnowledgeBase = builder.build()

        if (newKnowledgeBase == null) {
            logger.error("Could not create knowledge base.")
            throw ProgramResult(-1)
        }

        state.knowledgeBase = newKnowledgeBase

        logger.success("Knowledge base created.")
    }
}