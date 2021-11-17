package de.ahbnr.semanticweb.java_debugger.rdf.mapping

import com.sun.jdi.ThreadReference
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.reasoner.ReasonerRegistry
import java.io.BufferedReader
import java.io.FileReader

class GraphGenerator(
    private val applicationDomainRulesPath: String? /* turtle format file */,
    private val mappers: List<IMapper>
) {
    private fun loadJavaOntology(model: Model): Model {
        val owlInputStream = javaClass.getResourceAsStream("/ontologies/java.owl")
        model.read(owlInputStream, null, "TURTLE")

        return model
    }

    private fun mapProgramState(thread: ThreadReference, model: Model): Model {
        val vm = thread.virtualMachine()
        for (mapper in mappers) {
            mapper.extendModel(vm, thread, model)
        }

        return model
    }

    private fun loadApplicationDomain(model: Model): Model {
        if (applicationDomainRulesPath != null) {
            val fileReader = BufferedReader(FileReader(applicationDomainRulesPath))
            model.read(fileReader, null, "TURTLE")
        }

        return model;
    }

    fun getGraphModel(thread: ThreadReference): Model {
        var model = ModelFactory.createDefaultModel()
        //val ontManager = OntManagers.createManager()
        //val ontology = ontManager.createOntology()

        //var model: Model = ontology.asGraphModel()

        // Load Java ontology
        model = loadJavaOntology(model)

        // Load application domain knowledge
        model = loadApplicationDomain(model)

        // Map program state
        model = mapProgramState(thread, model)


        /**
         * Maybe try out forward vs. backward chaining by selecting and configuring reasoner manually.
         * See slide 66, lecture 6
         */
        // val reasoner = ReasonerRegistry.getOWLReasoner(); // sloooooow
        // val reasoner = ReasonerRegistry.getOWLMiniReasoner() // also not fast enough
        val reasoner = ReasonerRegistry.getOWLMicroReasoner() // much faster!
        // val reasoner = ReasonerRegistry.getRDFSReasoner() // fast, but not powerful enough for our purposes.
        return ModelFactory.createInfModel(reasoner, model)
        //return model
    }
}