@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands.assertcommands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import de.ahbnr.semanticweb.sjdb.repl.commands.REPLCommand
import de.ahbnr.semanticweb.sjdb.utils.UsabilityPreprocessor
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryParseException
import org.koin.core.component.KoinComponent

class SparqlAssertCommand : REPLCommand(name = "sparql"), KoinComponent {
    private val successMode = "success"
    private val failMode = "fail"

    private val mode: String by argument().choice(successMode, failMode)
    private val assertionExpression: String by argument()

    private val noReasoner by option()
        .flag(default = false)
        .help("do not use an OWL reasoner to answer the query.")

    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        val model = knowledgeBase.getSparqlModel(
            dontUseReasoner = noReasoner,
            customBaseOntology = null
        )

        val queryString = ParameterizedSparqlString(
            UsabilityPreprocessor.preprocess(assertionExpression)
        )

        try {
            for ((prefixName, prefixUri) in knowledgeBase.prefixNameToUri) {
                queryString.setNsPrefix(prefixName, prefixUri)
            }

            queryString.append("LIMIT ")
            queryString.appendLiteral(1)

            val query = QueryFactory.create(queryString.toString())

            knowledgeBase.buildSparqlExecution(query, model, dontUseReasoner = noReasoner).use { execution ->
                val results = execution.execSelect()
                // Need to ensure all variables are present in at least one result
                val result =
                    results.asSequence().find { result -> query.resultVars.all { result.contains(it) } }
                val wasSuccessful = result != null

                val assertionResult = when (mode) {
                    successMode -> {
                        if (wasSuccessful) {
                            logger.success("PASSED.")
                            true
                        } else {
                            logger.error("FAILED!")
                            false
                        }
                    }
                    failMode -> {
                        if (wasSuccessful) {
                            logger.error("FAILED!")
                            logger.error("Found the following result: $result")

                            false
                        } else {
                            logger.success("PASSED.")
                            true
                        }
                    }
                    else -> {
                        logger.error("Unknown sparql assertion mode: $mode")
                        throw ProgramResult(-1)
                    }
                }

                throw ProgramResult(if (assertionResult) 0 else -1)
            }
        } catch (e: QueryParseException) {
            logger.error(e.message ?: "Could not parse query.")
            throw ProgramResult(-1)
        }
    }
}