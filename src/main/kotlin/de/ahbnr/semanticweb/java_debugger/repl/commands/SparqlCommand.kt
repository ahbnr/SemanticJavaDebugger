@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.optimization.extractSyntacticLocalityModule
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryParseException
import org.apache.jena.query.ResultSetFormatter
import org.apache.jena.rdf.model.RDFNode
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SparqlCommand(
    private val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    override val name = "sparql"

    private val nonOptionRegex = """\s[^-\s]""".toRegex()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val baseOntology = repl.knowledgeBase
        if (baseOntology == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            return false
        }

        var options: List<String> = emptyList()
        var rawQuery: String = rawInput
        if (rawInput.startsWith(' ') || rawInput.startsWith('-')) {
            val nonOptionPosition = nonOptionRegex.find(rawInput)
            if (nonOptionPosition == null) {
                logger.error("No query has been specified.")
                return false
            }
            val startOfQuery = nonOptionPosition.range.first
            options = rawInput.substring(0 until startOfQuery).split(' ')
            rawQuery = rawInput.substring(startOfQuery)
        }

        val domainURI = baseOntology.asGraphModel().getNsPrefixURI("domain")
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
                $rawQuery
        """.trimIndent()

        try {
            val query = QueryFactory.create(queryString)

            val ontology = if (options.contains("--optimize")) {
                logger.debug("Axioms before module extraction: ${baseOntology.axiomCount}.")
                val module = extractSyntacticLocalityModule(baseOntology, query.queryPattern)
                logger.debug("Axioms after module extraction: ${module.axiomCount}.")
                module
            } else baseOntology

            val model = graphGenerator.buildInferredModel(ontology)

            QueryExecutionFactory.create(query, model).use { execution ->
                val results = execution.execSelect().rewindable()
                ResultSetFormatter.out(logger.logStream(), results, query)
                results.reset()

                for (variable in query.resultVars) {
                    repl.namedNodes.remove(variable)
                }

                var idx = 0;
                val nameMap = mutableMapOf<String, RDFNode>()
                results.forEachRemaining { solution ->
                    solution.varNames().forEachRemaining { variableName ->
                        val indexedName = if (idx == 0) variableName else "$variableName$idx"

                        nameMap[indexedName] = solution.get(variableName)
                    }
                    ++idx
                }
                repl.namedNodes.putAll(nameMap)

                if (nameMap.isNotEmpty()) {
                    logger.log("The solution variables are available under the following names:")
                    logger.log(nameMap.keys.joinToString(", "))
                }
            }
        } catch (e: QueryParseException) {
            logger.error(e.message ?: "Could not parse query.")
            return false
        }

        return true
    }
}