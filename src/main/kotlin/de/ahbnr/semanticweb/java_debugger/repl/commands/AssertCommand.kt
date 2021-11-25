@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryParseException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AssertCommand(
    private val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    override val name = "assert"

    private sealed class AssertionKind(val name: String) {
        object SparqlSuccess : AssertionKind("sparql-success")
        object SparqlError : AssertionKind("sparql-fail")

        companion object {
            val all = arrayOf(SparqlSuccess, SparqlError)

            fun getByName(name: String): AssertionKind? =
                all.firstOrNull { it.name == name }
        }
    }

    private val usage = """
        Usage: assert [${AssertionKind.all.joinToString("|") { it.name }}] <sparql WHERE clause>
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val ontology = repl.knowledgeBase
        if (ontology == null) {
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

        val queryString = ParameterizedSparqlString(
            rawInput.drop(subCommand.length)
        )
        val model = graphGenerator.buildInferredModel(ontology)

        try {
            queryString.setNsPrefix("rdf", URIs.ns.rdf)
            queryString.setNsPrefix("rdfs", URIs.ns.rdfs)
            queryString.setNsPrefix("owl", URIs.ns.owl)
            queryString.setNsPrefix("xsd", URIs.ns.xsd)
            queryString.setNsPrefix("java", URIs.ns.java)
            queryString.setNsPrefix("prog", URIs.ns.prog)
            queryString.setNsPrefix("run", URIs.ns.run)

            val domainURI = model.getNsPrefixURI("domain")
            if (domainURI != null) {
                queryString.setNsPrefix("PREFIX", domainURI)
            }

            queryString.append("LIMIT ")
            queryString.appendLiteral(1)

            val query = QueryFactory.create(queryString.toString())

            QueryExecutionFactory.create(query, model).use { execution ->
                val results = execution.execSelect()

                return when (assertionKind) {
                    is AssertionKind.SparqlSuccess -> {
                        if (results.hasNext()) {
                            logger.success("PASSED.")
                            true
                        } else {
                            logger.error("FAILED!")
                            false
                        }
                    }
                    is AssertionKind.SparqlError -> {
                        if (results.hasNext()) {
                            logger.error("FAILED!")
                            logger.error("Found the following result: ${results.next()}")

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
}