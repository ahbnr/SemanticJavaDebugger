package de.ahbnr.semanticweb.java_debugger

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import openllet.core.vocabulary.BuiltinNamespace
import openllet.jena.BuiltinTerm
import org.apache.jena.rdf.model.Model
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RdfSanityChecker : KoinComponent {
    private val URIs: OntURIs by inject()
    private val logger: Logger by inject()

    fun checkRdfTyping(model: Model) {
        val typeProperty = model.getProperty(URIs.rdf.type)

        // We utilize the namespace definitions of Openllet here to check for typos etc in terms from well known namespaces like "owl:Restriction"
        model
            .listObjectsOfProperty(typeProperty)
            .forEach { obj ->
                val node = obj.asNode()

                val builtinTerm = BuiltinTerm.find(node)
                if (builtinTerm == null) {
                    val builtinNamespace = BuiltinNamespace.find(node.nameSpace)
                    if (builtinNamespace != null) {
                        logger.error("Warning: The term ${node.localName} is not known in namespace ${node.nameSpace}.")
                    }
                }
            }

        // TODO: There is potentially a lot more sanity checks we can do
        //  (Typos, all the stuff that openllet.jena.graph.loader.DefaultGraphLoader is checking, ...)
    }
}