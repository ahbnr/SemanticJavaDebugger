@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.linting.ModelSanityChecker
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.ParserException
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.ErrorHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AddTriplesCommand : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    override val name = "add-triples"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val knowledgeBase = repl.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            return false
        }

        val prefixes = knowledgeBase
            .prefixNameToUri
            .entries
            .joinToString("\n") { (prefixName, prefixUri) -> "PREFIX $prefixName: <$prefixUri>" }

        val triplesString = """
                $prefixes
                $rawInput
        """.trimIndent()

        RDFParser
            .source(triplesString.byteInputStream())
            .lang(Lang.TURTLE)
            .errorHandler(object : ErrorHandler {
                private fun makeLogString(message: String, line: Long, col: Long): String =
                    "At $line:$col: $message"

                override fun error(message: String, line: Long, col: Long) {
                    logger.error("Parser Error. ${makeLogString(message, line, col)}")
                }

                override fun fatal(message: String, line: Long, col: Long) {
                    logger.error("FATAL Parser Error. ${makeLogString(message, line, col)}")
                    throw ParserException()
                }

                override fun warning(message: String, line: Long, col: Long) {
                    logger.error("Parser Warning: ${makeLogString(message, line, col)}")
                }
            })
            .strict(true)
            .checking(true)
            .parse(knowledgeBase.ontology.asGraphModel())

        ModelSanityChecker().fullCheck(knowledgeBase.ontology, knowledgeBase.limiter, false)

        return true
    }
}