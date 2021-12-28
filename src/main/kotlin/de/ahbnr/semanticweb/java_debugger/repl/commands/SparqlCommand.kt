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
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.optimization.extractSyntacticLocalityModule
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryParseException
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

        val prefixes = knowledgeBase
            .prefixNameToUri
            .entries
            .joinToString("\n") { (prefixName, prefixUri) -> "PREFIX $prefixName: <$prefixUri>" }

        val queryString = """
                $prefixes
                $rawSparqlExpression
        """.trimIndent()

        try {
            val query = QueryFactory.create(queryString)

            val ontology = when (val it = moduleExtraction) {
                is SyntacticExtractionOptions -> {
                    logger.debug("Axioms before module extraction: ${knowledgeBase.ontology.axiomCount}.")
                    val module =
                        extractSyntacticLocalityModule(knowledgeBase, query.queryPattern, it.classRelationDepth)
                    logger.debug("Axioms after module extraction: ${module.axiomCount}.")
                    module
                }

                else -> knowledgeBase.ontology
            }

            val model = knowledgeBase.getSparqlModel(ontology)

            knowledgeBase.buildSparqlExecution(query, model).use { execution ->
                val results = execution.execSelect().rewindable()
                ResultSetFormatter.out(logger.logStream(), results, query)
                results.reset()

                for (variable in query.resultVars) {
                    knowledgeBase.removeVariable("?$variable")
                }

                var idx = 0;
                val nameMap = mutableMapOf<String, RDFNode>()
                results.forEachRemaining { solution ->
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
        } catch (e: QueryParseException) {
            logger.error(e.message ?: "Could not parse query.")
            throw ProgramResult(-1)
        }
    }
}