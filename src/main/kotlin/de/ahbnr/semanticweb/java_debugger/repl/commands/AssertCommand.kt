@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryParseException
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.SimpleSelector
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.riot.RDFParser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AssertCommand(
    private val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    override val name = "assert"

    private sealed class AssertionKind(val name: String) {
        sealed class SparqlAssertion(val subName: String) : AssertionKind(subName)

        object SparqlSuccess : SparqlAssertion("sparql-success")
        object SparqlFail : SparqlAssertion("sparql-fail")
        object Triples : AssertionKind("triples")

        companion object {
            val all = arrayOf(SparqlSuccess, SparqlFail, Triples)

            fun getByName(name: String): AssertionKind? =
                all.firstOrNull { it.name == name }
        }
    }

    private val usage = """
        Usage: assert [${AssertionKind.all.joinToString("|") { it.name }}] <sparql WHERE clause>
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val knowledgeBase = repl.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            return false
        }

        val subCommand = argv.firstOrNull()
        if (subCommand == null) {
            logger.error(usage)
            return false
        }

        val assertionKind = AssertionKind.getByName(subCommand)
        if (assertionKind == null) {
            logger.error(usage)
            return false
        }

        when (assertionKind) {
            is AssertionKind.SparqlAssertion -> {
                val model = knowledgeBase.getSparqlModel()

                val queryString = ParameterizedSparqlString(
                    rawInput.drop(subCommand.length)
                )

                try {
                    for ((prefixName, prefixUri) in knowledgeBase.prefixNameToUri) {
                        queryString.setNsPrefix(prefixName, prefixUri)
                    }

                    queryString.append("LIMIT ")
                    queryString.appendLiteral(1)

                    val query = QueryFactory.create(queryString.toString())

                    knowledgeBase.buildSparqlExecution(query, model).use { execution ->
                        val results = execution.execSelect()
                        // Need to ensure all variables are present in at least one result
                        val result =
                            results.asSequence().find { result -> query.resultVars.all { result.contains(it) } }
                        val wasSuccessful = result != null

                        return when (assertionKind) {
                            is AssertionKind.SparqlSuccess -> {
                                if (wasSuccessful) {
                                    logger.success("PASSED.")
                                    true
                                } else {
                                    logger.error("FAILED!")
                                    false
                                }
                            }
                            is AssertionKind.SparqlFail -> {
                                if (wasSuccessful) {
                                    logger.error("FAILED!")
                                    logger.error("Found the following result: ${result}")

                                    false
                                } else {
                                    logger.success("PASSED.")
                                    true
                                }
                            }
                        }
                    }
                } catch (e: QueryParseException) {
                    logger.error(e.message ?: "Could not parse query.")
                    return false
                }
            }
            is AssertionKind.Triples -> {
                val model = knowledgeBase.getTripleListingModel()

                val prefixes = knowledgeBase
                    .prefixNameToUri
                    .entries
                    .joinToString("\n") { (prefixName, prefixUri) -> "PREFIX $prefixName: <$prefixUri>" }

                val targetModel = ModelFactory.createDefaultModel()

                RDFParser
                    .create()
                    .source(
                        """
                            $prefixes
                            ${rawInput.drop(subCommand.length)}
                        """
                            .trimIndent()
                            .byteInputStream()
                    )
                    .lang(RDFLanguages.TTL)
                    .parse(targetModel)

                for (statement in targetModel.listStatements()) {
                    if (statement.subject.isAnon || statement.`object`.isAnon) {
                        logger.error("Blank nodes are not supported here.")
                        return false
                    }

                    val selector = SimpleSelector(statement.subject, statement.predicate, statement.`object`)

                    if (model.query(selector).isEmpty) {
                        logger.error("FAILED!")
                        logger.log("Triple not found: $statement.")
                        return false
                    }
                }

                logger.success("PASSED")
                return true
            }
        }
    }
}