package de.ahbnr.semanticweb.java_debugger.repl

import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import org.apache.jena.rdf.model.RDFNode
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class KnowledgeBase : KoinComponent {
    private val URIs: OntURIs by inject()

    var ontology: Ontology? = null
        set(value) {
            field = value
            variableStore.clear()
            buildPrefixMaps(value)
        }

    var prefixNameToUri = emptyMap<String, String>()
        private set
    var uriToPrefixName = emptyMap<String, String>()
        private set

    private val variableStore: MutableMap<String, RDFNode> = mutableMapOf()
    val variables: Set<String>
        get() = variableStore.keys

    private fun buildPrefixMaps(ontology: Ontology?) {
        if (ontology == null) {
            prefixNameToUri = emptyMap()
            uriToPrefixName = emptyMap()
            return
        }

        val prefixNameToIRI = mutableMapOf(
            "rdf" to URIs.ns.rdf,
            "rdfs" to URIs.ns.rdfs,
            "owl" to URIs.ns.owl,
            "xsd" to URIs.ns.xsd,
            "java" to URIs.ns.java,
            "prog" to URIs.ns.prog,
            "run" to URIs.ns.run,
        )

        val domainURI = ontology.asGraphModel().getNsPrefixURI("domain")
        if (domainURI != null) {
            prefixNameToIRI["domain"] = domainURI
        }

        this.prefixNameToUri = prefixNameToIRI
        this.uriToPrefixName = prefixNameToIRI
            .entries
            .associate { (prefixName, prefix) -> prefix to prefixName }
    }

    private fun assertIsVariableName(name: String) {
        if (name.isBlank()) {
            throw IllegalArgumentException("Variable names may not be blank.")
        }

        if (name[0] != '?') {
            throw IllegalArgumentException("All variable names must start with a '?'.")
        }
    }

    fun setVariable(name: String, value: RDFNode) {
        assertIsVariableName(name)
        variableStore[name] = value
    }

    fun getVariable(name: String): RDFNode? {
        assertIsVariableName(name)
        return variableStore.getOrDefault(name, null)
    }

    fun removeVariable(name: String) {
        assertIsVariableName(name)
        variableStore.remove(name)
    }

    fun resolvePrefixNameInUri(uri: String): String {
        val prefixNameAndUri =
            prefixNameToUri.entries.find { (prefixName, _) -> uri.startsWith("$prefixName:") }

        return if (prefixNameAndUri != null) {
            val (prefixName, prefixUri) = prefixNameAndUri

            uri.replaceRange(0 until prefixName.length + 1, prefixUri)
        } else uri
    }

    fun resolveVariableOrUri(variableOrUri: String): RDFNode? =
        if (variableOrUri.startsWith("?")) {
            // Its a variable if it starts with a '?'
            variableStore.getOrDefault(variableOrUri, null)
        } else {
            // We treat it as an IRI otherwise
            ontology?.asGraphModel()?.getIndividual(resolvePrefixNameInUri(variableOrUri))
        }
}