@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import de.ahbnr.semanticweb.java_debugger.rdf.linting.LinterMode
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.KnowledgeBaseBuilder
import org.koin.core.component.KoinComponent

class BuildKBCommand(
    val graphGenerator: GraphGenerator
) : REPLCommand(name = "buildkb"), KoinComponent {
    val limitSdk: Boolean by option().flag(default = false)
    val linting: LinterMode by option().choice(
        "default" to LinterMode.Normal,
        "full" to LinterMode.FullReport,
        "none" to LinterMode.NoLinters
    ).default(LinterMode.Normal)

    val deep: List<String> by option().multiple()

    override fun run() {
        val jvmState = tryGetJvmState()

        val builder = KnowledgeBaseBuilder(
            graphGenerator = graphGenerator,
            sourcePath = state.sourcePath,
            applicationDomainDefFile = state.applicationDomainDefFile,
            jvmState = jvmState,
            limitSdk = limitSdk,
            deepFieldsAndVariables = deep.toSet(),
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