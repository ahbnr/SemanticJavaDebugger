@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.ModuleExtractionOptions
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.SparqlExecutor
import org.apache.jena.query.ResultSetFormatter
import org.apache.jena.rdf.model.RDFNode
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SparqlCommand : REPLCommand(name = "sparql"), KoinComponent {
    private val logger: Logger by inject()

    class SyntacticExtractionOptions : OptionGroup() {
        val classRelationDepth by option().int().default(-1)
    }

    val moduleExtraction by option().groupChoice(
        "syntactic" to SyntacticExtractionOptions()
    )

    val rawSparqlExpression: String by argument()

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        val executor = SparqlExecutor(
            knowledgeBase,
            moduleExtractionOptions = when (val it = moduleExtraction) {
                is SyntacticExtractionOptions -> ModuleExtractionOptions.SyntacticExtraction(
                    classRelationDepth = it.classRelationDepth
                )

                else -> ModuleExtractionOptions.NoExtraction
            }
        )

        val execution = executor
            .execute(rawSparqlExpression)
            ?: throw ProgramResult(-1)

        execution.use {
            val rewindableResultSet = it.execSelect().rewindable()

            ResultSetFormatter.out(logger.logStream(), rewindableResultSet, it.query)
            rewindableResultSet.reset()

            for (variable in it.query.resultVars) {
                knowledgeBase.removeVariable("?$variable")
            }

            var idx = 0;
            val nameMap = mutableMapOf<String, RDFNode>()
            rewindableResultSet.forEachRemaining { solution ->
                solution.varNames().forEachRemaining { variableName ->
                    val indexedName = if (idx == 0) "?$variableName" else "?$variableName$idx"

                    nameMap[indexedName] = solution.get(variableName)
                }
                ++idx
            }
            nameMap.forEach { (name, value) -> knowledgeBase.setVariable(name, value) }

            if (nameMap.isNotEmpty()) {
                logger.log("The solution variables are available under the following names:")
                logger.log(nameMap.keys.joinToString(", "))
            }
        }
    }
}