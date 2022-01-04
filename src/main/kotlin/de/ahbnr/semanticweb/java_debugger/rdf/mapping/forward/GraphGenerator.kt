@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import com.github.owlcs.ontapi.OntManagers
import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.linting.LinterMode
import de.ahbnr.semanticweb.java_debugger.rdf.linting.ModelSanityChecker
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.Namespaces
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.TurtleReader
import org.apache.jena.rdf.model.Model
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.SequenceInputStream

class ParserException() : Exception()

class GraphGenerator(
    private val ns: Namespaces,
    private val mappers: List<IMapper>
) : KoinComponent {
    private val logger: Logger by inject()

    private fun readIntoModel(model: Model, inputStream: InputStream) {
        val reader = TurtleReader(inputStream)
        reader.readInto(model)
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
            val prefixStream = model
                .nsPrefixMap
                .entries
                .joinToString("\n") { (prefixName, prefixUri) -> "@prefix $prefixName: <$prefixUri> ." }
                .byteInputStream()

            val fileStream = FileInputStream(File(applicationDomainRulesPath))

            val domainInputStream = SequenceInputStream(
                prefixStream,
                fileStream
            )

            readIntoModel(model, domainInputStream)
        }
    }

    fun buildOntology(
        buildParameters: BuildParameters,
        applicationDomainRulesPath: String?, /* turtle format file */
        linterMode: LinterMode
    ): Ontology? {
        // var model = ModelFactory.createDefaultModel()

        val ontManager = OntManagers.createManager()
        val ontology = ontManager.createOntology()

        val model: Model = ontology.asGraphModel()

        try {
            // Load Java ontology
            loadJavaOntology(model)

            // Map program state
            mapProgramState(buildParameters, model)

            // Load application domain knowledge
            loadApplicationDomain(applicationDomainRulesPath, model)
        } catch (e: ParserException) {
            logger.log("Aborted model building due to fatal parser error.")
            return null
        }

        // Perform sanity checks and linting
        val checker = ModelSanityChecker()
        checker.fullCheck(ontology, buildParameters.limiter, linterMode)

        return ontology
    }
}