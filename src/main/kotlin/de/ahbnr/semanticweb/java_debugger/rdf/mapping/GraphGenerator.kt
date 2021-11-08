package de.ahbnr.semanticweb.java_debugger.rdf.mapping

import com.sun.jdi.ThreadReference
import com.sun.jdi.VirtualMachine
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory

class GraphGenerator(private val mappers: List<IMapper>) {
    private fun loadJavaOntology(model: Model) {
        val owlInputStream = javaClass.getResourceAsStream("/ontologies/java.owl")
        model.read(owlInputStream, null, "TURTLE")
    }

    fun getGraphModel(vm: VirtualMachine, thread: ThreadReference): Model {
        val model = ModelFactory.createDefaultModel()

        // Load Java ontology
        loadJavaOntology(model)

        // Map program state
        for (mapper in mappers) {
            mapper.extendModel(vm, thread, model)
        }

        return model
    }
}