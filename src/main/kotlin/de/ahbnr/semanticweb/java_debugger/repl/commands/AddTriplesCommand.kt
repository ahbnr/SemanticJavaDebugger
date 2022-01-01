@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.linting.LinterMode
import de.ahbnr.semanticweb.java_debugger.rdf.linting.ModelSanityChecker
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.ParserException
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.ErrorHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AddTriplesCommand : REPLCommand(name = "add-triples"), KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    val triplesString: String by argument()

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        val prefixes = knowledgeBase
            .prefixNameToUri
            .entries
            .joinToString("\n") { (prefixName, prefixUri) -> "PREFIX $prefixName: <$prefixUri>" }

        val triplesString = """
                $prefixes
                $triplesString
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

        ModelSanityChecker().fullCheck(knowledgeBase.ontology, knowledgeBase.buildParameters.limiter, LinterMode.Normal)
    }
}