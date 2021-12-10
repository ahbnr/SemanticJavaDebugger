@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import com.github.owlcs.ontapi.OntManagers
import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.Namespaces
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.RiotException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.BufferedReader
import java.io.FileReader
import java.io.PrintStream

class GraphGenerator(
    private val ns: Namespaces,
    private val mappers: List<IMapper>
) : KoinComponent {
    private val logger: Logger by inject()

    private fun loadJavaOntology(model: Model) {
        val owlInputStream = javaClass.getResourceAsStream("/ontologies/java.owl")
        model.read(owlInputStream, null, "TURTLE")
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
            val fileReader = BufferedReader(FileReader(applicationDomainRulesPath))

            try {
                model.read(fileReader, null, "TURTLE")
            } catch (e: RiotException) {
                val printStream = PrintStream(logger.logStream())
                e.printStackTrace(printStream)
                printStream.flush()
                logger.error("Failed to parse domain specification from ${applicationDomainRulesPath}.")
            }
        }
    }

    fun buildOntology(
        buildParameters: BuildParameters,
        applicationDomainRulesPath: String?, /* turtle format file */
    ): Ontology {
        // var model = ModelFactory.createDefaultModel()

        val ontManager = OntManagers.createManager()
        val ontology = ontManager.createOntology()

        val model: Model = ontology.asGraphModel()

        // Load Java ontology
        loadJavaOntology(model)

        // Load application domain knowledge
        loadApplicationDomain(applicationDomainRulesPath, model)

        // Map program state
        mapProgramState(buildParameters, model)

        return ontology
    }
}