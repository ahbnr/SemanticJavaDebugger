package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.ParserException
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.ErrorHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.InputStream

class TurtleReader(val inputStream: InputStream) : KoinComponent {
    private val logger: Logger by inject()

    fun readInto(model: Model) {
        RDFParser
            .source(inputStream)
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
            .parse(model)
    }
}