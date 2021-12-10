@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class StatsCommand(
    private val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    override val name = "stats"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val knowledgeBase = repl.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            return false
        }

        logger.log("Number of ontology axioms: ${knowledgeBase.ontology.axiomCount}")

        knowledgeBase.ontology.asGraphModel().let { plainModel ->
            logger.log("Number of statements in Jena Model (without inference): ${plainModel.graph.size()}.")
            logger.log("")

            data class Countable(val name: String, val uri: String)

            val toCount = listOf(
                Countable("Classes", URIs.java.Class),
                Countable("Interfaces", URIs.java.Interface),
                Countable("Methods", URIs.java.Method),
                Countable("Fields", URIs.java.Field),
                Countable("Array Types", URIs.java.Array),
                Countable("Variable Declarations", URIs.java.VariableDeclaration),
                Countable("Objects", URIs.java.Object),
                Countable("Stack Frames", URIs.java.StackFrame),
            )

            val typeProperty = plainModel.getProperty(URIs.rdf.type)
            fun countSubjects(countable: Countable): Int {
                val subjects =
                    plainModel.listSubjectsWithProperty(typeProperty, plainModel.getResource(countable.uri))
                var count = 0
                for (subject in subjects) ++count

                return count
            }

            toCount.forEach {
                val count = countSubjects(it)

                logger.log("${it.name}: $count")
            }
        }

        knowledgeBase.getSparqlModel().let { infModel ->
            val prefixes = knowledgeBase
                .prefixNameToUri
                .entries
                .joinToString("\n") { (prefixName, prefixUri) -> "PREFIX $prefixName: <$prefixUri>" }

            // Arrays are deeply inspected if a cardinality is set for their hasElement relation
            val deepArrayQueryString = """
                    $prefixes
                    SELECT (count(distinct ?sizedHasElement) as ?count)
                    WHERE {
                        ?sizedHasElement rdfs:subPropertyOf java:hasElement ;
                                         owl:cardinality [] .
                    }
            """.trimIndent()

            val deepArrayQuery = QueryFactory.create(deepArrayQueryString)
            QueryExecutionFactory.create(deepArrayQuery, infModel).use { execution ->
                val results = execution.execSelect()

                logger.log("Deep Arrays: ${results.nextSolution().get("count").asLiteral().int}")
                logger.log("")
            }
        }

        return true
    }
}