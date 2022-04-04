package de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils

import com.github.owlcs.ontapi.OntManagers
import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.ParserException
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.RiotException
import org.apache.jena.riot.system.ErrorHandler
import org.apache.jena.util.FileUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.io.UnparsableOntologyException
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy
import java.io.InputStream

class UniversalKnowledgeBaseParser(
    val model: Model,
    val fileName: String?,
    val inputStream: InputStream
) : KoinComponent {
    private val logger: Logger by inject()

    private fun readWithJena(lang: Lang): Boolean {
        try {
            val parsingModel = ModelFactory.createDefaultModel()

            RDFParser
                .source(inputStream)
                .lang(lang)
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
                .parse(parsingModel)

            model.add(parsingModel)

            return true
        } catch (e: RiotException) {
        } catch (e: ParserException) {
        }

        return false
    }

    private fun readWithOwlApi(): Boolean {
        val loadingManager = OntManagers.createManager()
        loadingManager.ontologyLoaderConfiguration =
            loadingManager.ontologyLoaderConfiguration.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT)

        return try {
            // If Jena parsers fail, try OWLAPI parsers, since the file might be in functional syntax or manchester
            // syntax
            val loadedOntology = loadingManager.loadOntologyFromOntologyDocument(inputStream)

            model.add(loadedOntology.asGraphModel())
            true
        } catch (e: UnparsableOntologyException) {
            logger.warning("OWLAPI parser failed: ${e.message}. Trying Jena RDF parsers next.")
            false
        } finally {
            loadingManager.clearOntologies()
        }
    }

    fun readIntoModel() {
        if (fileName == null || fileName.endsWith("owl")) {
            if (readWithOwlApi()) return
        }

        val guessedLang = when (FileUtils.guessLang(fileName)) {
            FileUtils.langN3 -> Lang.N3
            FileUtils.langTurtle -> Lang.TURTLE
            FileUtils.langNTriple -> Lang.NTRIPLES
            FileUtils.langXML -> Lang.RDFXML
            FileUtils.langXMLAbbrev -> Lang.RDFXML
            else -> {
                logger.warning("Could not determine file type automatically. Will now try all available Jena parsers.")
                null
            }
        }

        if (guessedLang != null) {
            if (readWithJena(guessedLang)) return
            else {
                logger.warning("Jena parser for $guessedLang failed. Will now try all available Jena parsers.")
            }
        }


        val langsToTry = sequenceOf(
            Lang.RDFXML,
            Lang.TURTLE,
            Lang.N3,
            Lang.NTRIPLES,
            Lang.NT,
            Lang.JSONLD,
            Lang.JSONLD11,
            Lang.RDFJSON,
            Lang.TRIG,
            Lang.NQUADS,
            Lang.NQ,
            Lang.RDFTHRIFT,
            Lang.SHACLC,
            Lang.CSV,
            Lang.TSV,
            Lang.TRIX,
            Lang.RDFNULL,
        )

        for (lang in langsToTry) {
            if (readWithJena(lang)) return
        }

        logger.error("Tried all parsers. Could not parse file.")
        throw RuntimeException()
    }
}
