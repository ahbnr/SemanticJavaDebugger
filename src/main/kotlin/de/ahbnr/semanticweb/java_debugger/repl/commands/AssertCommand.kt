@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import de.ahbnr.semanticweb.java_debugger.utils.expandResourceToModel
import de.ahbnr.semanticweb.java_debugger.utils.toPrettyString
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryParseException
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AssertCommand(
    private val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    override val name = "assert"

    private val usage = """
        Usage: assert [exists|not-exists] <sparql WHERE clause>
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

        val isExistsAssert = when (subCommand) {
            "exists" -> true
            "not-exists" -> false
            else -> {
                logger.error(usage)
                return false
            }
        }

        val whereClause = rawInput.drop(subCommand.length)

        val model = graphGenerator.buildInferredModel(ontology)

        val domainURI = model.getNsPrefixURI("domain")
        val sparqlPrefixString = if (domainURI != null) "PREFIX domain: <$domainURI>" else ""

        val queryString = """
                PREFIX rdf: <${URIs.ns.rdf}>
                PREFIX rdfs: <${URIs.ns.rdfs}>
                PREFIX owl: <${URIs.ns.owl}>
                PREFIX xsd: <${URIs.ns.xsd}>
                PREFIX java: <${URIs.ns.java}>
                PREFIX prog: <${URIs.ns.prog}>
                PREFIX run: <${URIs.ns.run}>
                $sparqlPrefixString
                SELECT ?output
                WHERE {
                    $whereClause
                }
                LIMIT 1
        """.trimIndent()

        try {
            val query = QueryFactory.create(queryString)

            QueryExecutionFactory.create(query, model).use { execution ->
                val results = execution.execSelect()

                if (isExistsAssert) {
                    if (results.hasNext()) {
                        logger.success("PASSED.")
                    } else {
                        logger.error("FAILED!")
                        return false
                    }
                } else {
                    if (results.hasNext()) {
                        logger.error("FAILED!")
                        logger.error("Found the following result:")

                        val node = results.nextSolution().get("output")
                        when {
                            node.isResource -> {
                                val resource = node.asResource()
                                RDFDataMgr.write(logger.logStream(), expandResourceToModel(resource, URIs.ns), Lang.TTL)
                            }
                            else -> {
                                logger.log(node.toPrettyString(model))
                            }
                        }

                        return false
                    } else {
                        logger.success("PASSED.")
                    }
                }

                return true
            }
        } catch (e: QueryParseException) {
            logger.error(e.message ?: "Could not parse query.")
            return false
        }
    }
}