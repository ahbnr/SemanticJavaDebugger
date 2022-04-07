package de.ahbnr.semanticweb.sjdb.repl.commands.utils

import de.ahbnr.semanticweb.jdi2owl.Logger
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import de.ahbnr.semanticweb.sjdb.utils.UsabilityPreprocessor
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryParseException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SparqlExecutor(
    private val knowledgeBase: KnowledgeBase,
    private val moduleExtractionOptions: ModuleExtractionOptions
) : KoinComponent {
    private val logger: Logger by inject()

    fun execute(query: String): QueryExecution? {
        val preprocessedQuery = UsabilityPreprocessor.preprocess(query)

        val prefixes = knowledgeBase
            .prefixNameToUri
            .entries
            .joinToString("\n") { (prefixName, prefixUri) -> "PREFIX $prefixName: <$prefixUri>" }

        val prefixedQuery = """
                $prefixes
                $preprocessedQuery
        """.trimIndent()

        return try {
            val query = QueryFactory.create(prefixedQuery)

            val ontology = when (moduleExtractionOptions) {
                is ModuleExtractionOptions.SyntacticExtraction -> {
                    val extractor = SyntacticLocalityModuleExtractor(
                        knowledgeBase,
                        depth = moduleExtractionOptions.classRelationDepth,
                        quiet = false
                    )

                    extractor.extractModule(query.queryPattern)
                }

                is ModuleExtractionOptions.NoExtraction -> knowledgeBase.ontology
            }

            val model = knowledgeBase.getSparqlModel(ontology)

            knowledgeBase.buildSparqlExecution(query, model)
        } catch (e: QueryParseException) {
            logger.error(e.message ?: "Could not parse query.")
            null
        }
    }
}
