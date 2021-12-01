@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import com.github.owlcs.ontapi.OntManagers
import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.Namespaces
import org.apache.jena.rdf.model.InfModel
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.reasoner.ReasonerRegistry
import java.io.BufferedReader
import java.io.FileReader

class GraphGenerator(
    private val ns: Namespaces,
    private val mappers: List<IMapper>
) {
    private fun loadJavaOntology(model: Model) {
        val owlInputStream = javaClass.getResourceAsStream("/ontologies/java.owl")
        model.read(owlInputStream, null, "TURTLE")
    }

    private fun mapProgramState(jvmState: JvmState, model: Model, limiter: MappingLimiter) {
        model.setNsPrefix("run", ns.run)

        for (mapper in mappers) {
            mapper.extendModel(jvmState, model, limiter)
        }
    }

    private fun loadApplicationDomain(
        applicationDomainRulesPath: String?, /* turtle format file */
        model: Model
    ) {
        if (applicationDomainRulesPath != null) {
            val fileReader = BufferedReader(FileReader(applicationDomainRulesPath))
            model.read(fileReader, null, "TURTLE")
        }
    }

    fun buildOntology(
        jvmState: JvmState,
        applicationDomainRulesPath: String?, /* turtle format file */
        limiter: MappingLimiter
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
        mapProgramState(jvmState, model, limiter)

        return ontology
    }

    fun buildInferredModel(ontology: Ontology): InfModel {
        val model = ontology.asGraphModel()

        /**
         * Maybe try out forward vs. backward chaining by selecting and configuring reasoner manually.
         * See slide 66, lecture 6
         */
        // val reasoner = ReasonerRegistry.getOWLReasoner(); // sloooooow
        // val reasoner = ReasonerRegistry.getOWLMiniReasoner() // also not fast enough
        val reasoner = ReasonerRegistry.getOWLMicroReasoner() // much faster!
        // val reasoner = ReasonerRegistry.getRDFSReasoner() // fast, but not powerful enough for our purposes.

        return ModelFactory.createInfModel(reasoner, model)
    }
}