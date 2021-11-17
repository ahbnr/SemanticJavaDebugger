@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryParseException
import org.apache.jena.query.ResultSetFormatter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SparqlCommand(
    private val graphGenerator: GraphGenerator
): IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "sparql"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        val ontology = repl.knowledgeBase
        if (ontology == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            return
        }

        val model = graphGenerator.buildInferredModel(ontology)

        val domainURI = model.getNsPrefixURI("domain")
        val sparqlPrefixString = if (domainURI != null) "PREFIX domain: <$domainURI>" else ""

        val queryString = """
                PREFIX java: <https://github.com/ahbnr/SemanticJavaDebugger#>
                PREFIX prog: <https://github.com/ahbnr/SemanticJavaDebugger/Program#>
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

            val queryExecution = QueryExecutionFactory.create(query, model)
            val results = queryExecution.execSelect()

            ResultSetFormatter.out(logger.logStream(), results, query)
            queryExecution.close()
        }

        catch (e: QueryParseException) {
            logger.error(e.message ?: "Could not parse query.")
        }
    }
}