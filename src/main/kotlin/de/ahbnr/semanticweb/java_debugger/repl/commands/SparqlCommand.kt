@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.query.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SparqlCommand(
    private val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    override val name = "sparql"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        repl.queryResult = null
        repl.queryResultVars = null

        val ontology = repl.knowledgeBase
        if (ontology == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            return
        }

        val model = graphGenerator.buildInferredModel(ontology)

        val domainURI = model.getNsPrefixURI("domain")
        val sparqlPrefixString = if (domainURI != null) "PREFIX domain: <$domainURI>" else ""

        val queryString = """
                PREFIX rdf: <${URIs.ns.rdf}>
                PREFIX rdfs: <${URIs.ns.rdfs}>
                PREFIX owl: <${URIs.ns.owl}>
                PREFIX java: <${URIs.ns.java}>
                PREFIX prog: <${URIs.ns.prog}>
                PREFIX run: <${URIs.ns.run}>
                $sparqlPrefixString
                $rawInput
        """.trimIndent()

        try {
            val query = QueryFactory.create(queryString)

            // println("WRITING")
            // FileOutputStream("graph.rdf").use {
            //     model.write(it)
            // }

            //val query = QueryFactory.create("""
            //        PREFIX domain: <https://github.com/ahbnr/SemanticJavaDebugger/TwoThreeTree#>
            //        PREFIX prog: <https://github.com/ahbnr/SemanticJavaDebugger/Program#>
            //        PREFIX java: <https://github.com/ahbnr/SemanticJavaDebugger#>
            //        SELECT ?root ?var
            //        WHERE {
            //            ?root a domain:Root .
            //            ?var java:storesReferenceTo ?root .
            //        }
            //    """.trimIndent())

            //val query = QueryFactory.create("""
            //    PREFIX domain: <https://github.com/ahbnr/SemanticJavaDebugger/TwoThreeTree#>
            //    PREFIX prog: <https://github.com/ahbnr/SemanticJavaDebugger/Program#>
            //    PREFIX java: <https://github.com/ahbnr/SemanticJavaDebugger#>
            //    SELECT ?x
            //    WHERE { ?x a domain:Root }
            //""".trimIndent())

            //val query = QueryFactory.create("""
            //    PREFIX domain: <https://github.com/ahbnr/SemanticJavaDebugger/TwoThreeTree#>
            //    PREFIX prog: <https://github.com/ahbnr/SemanticJavaDebugger/Program#>
            //    PREFIX java: <https://github.com/ahbnr/SemanticJavaDebugger#>
            //    SELECT ?x
            //    WHERE { ?x prog:Node_parent java:null }
            //""".trimIndent())

            QueryExecutionFactory.create(query, model).use { execution ->
                val results = execution.execSelect().rewindable()
                ResultSetFormatter.out(logger.logStream(), results, query)
                results.reset()

                val resultList = mutableListOf<QuerySolution>()
                results.forEachRemaining(resultList::add)

                repl.queryResult = resultList
                repl.queryResultVars = results.resultVars.toSet()
            }
        } catch (e: QueryParseException) {
            logger.error(e.message ?: "Could not parse query.")
        }
    }
}