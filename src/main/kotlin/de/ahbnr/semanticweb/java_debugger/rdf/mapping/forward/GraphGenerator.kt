@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import com.github.owlcs.ontapi.OntManagers
import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.linting.ModelSanityChecker
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.Namespaces
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.ErrorHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

private class ParserException() : Exception()

class GraphGenerator(
    private val ns: Namespaces,
    private val mappers: List<IMapper>
) : KoinComponent {
    private val logger: Logger by inject()

    private fun readIntoModel(model: Model, inputStream: InputStream) {
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

    private fun loadJavaOntology(model: Model) {
        val owlInputStream = javaClass.getResourceAsStream("/ontologies/java.owl")

        readIntoModel(model, owlInputStream!!)
    }

    private fun mapProgramState(buildParameters: BuildParameters, model: Model) {
        model.setNsPrefix("run", ns.run)

        for (mapper in mappers) {
            mapper.extendModel(buildParameters, model)
        }
    }

    private fun loadApplicationDomain(
        applicationDomainRulesPath: String?, /* turtle format file */
        model: Model
    ) {
        if (applicationDomainRulesPath != null) {
            readIntoModel(model, FileInputStream(File(applicationDomainRulesPath)))
        }
    }

    fun buildOntology(
        buildParameters: BuildParameters,
        applicationDomainRulesPath: String?, /* turtle format file */
        fullLintingReport: Boolean
    ): Ontology? {
        // var model = ModelFactory.createDefaultModel()

        val ontManager = OntManagers.createManager()
        val ontology = ontManager.createOntology()

        val model: Model = ontology.asGraphModel()

        try {
            // Load Java ontology
            loadJavaOntology(model)

            // Load application domain knowledge
            loadApplicationDomain(applicationDomainRulesPath, model)
        } catch (e: ParserException) {
            logger.log("Aborted model building due to fatal parser error.")
            return null
        }

        // Map program state
        mapProgramState(buildParameters, model)

        // Perform sanity checks and linting
        val checker = ModelSanityChecker()
        checker.checkRdfTyping(model)
        // Too many "untyped" errors for IRIs from external ontologies e.g. rdf:List
        checker.openllintOwlSyntaxChecks(model, buildParameters.limiter, fullLintingReport)
        checker.OWL2DLProfileViolationTest(ontology)
        checker.openllintOwlPatternChecks(ontology)

        return ontology
    }
}