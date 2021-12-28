package de.ahbnr.semanticweb.java_debugger.repl

import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter
import org.apache.jena.query.Query
import org.apache.jena.query.QueryExecution
import org.apache.jena.rdf.model.InfModel
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.reasoner.OWLReasoner


class KnowledgeBase(
    val ontology: Ontology,
    val limiter: MappingLimiter
) : KoinComponent {
    private val URIs: OntURIs by inject()
    private val logger: Logger by inject()
    private val state: SemanticDebuggerState by inject()

    private val sparqlReasoner: ReasonerId
        get() = state.targetReasoner
    private val shaclReasoner: ReasonerId
        get() = state.targetReasoner
    private val tripleListingReasoner: ReasonerId
        get() = state.targetReasoner
    private val jenaValidationReasoner: JenaReasonerProvider
        get() = with(state.targetReasoner) {
            when (this) {
                is ReasonerId.PureJenaReasoner -> this
                is ReasonerId.PureOwlApiReasoner -> {
                    val fallback = ReasonerId.PureJenaReasoner.JenaOwlMicro
                    logger.debug("Can not use ${this.name} for Jena RDF validation. Falling back to ${fallback.name}.")
                    fallback
                }
                is ReasonerId.Openllet -> this
            }
        }
    private val owlClassExpressionReasoner: OwlApiReasonerProvider
        get() = with(state.targetReasoner) {
            when (this) {
                is ReasonerId.PureJenaReasoner -> {
                    val fallback = ReasonerId.PureOwlApiReasoner.HermiT
                    logger.debug("Can not use ${this.name} for owl class expression evaluation. Falling back to ${fallback.name}.")
                    fallback
                }
                is ReasonerId.PureOwlApiReasoner -> this
                is ReasonerId.Openllet -> this
            }
        }
    private val consistencyReasoner: OwlApiReasonerProvider
        get() = with(state.targetReasoner) {
            when (this) {
                is ReasonerId.PureJenaReasoner -> {
                    val fallback = ReasonerId.PureOwlApiReasoner.HermiT
                    logger.debug("Can not use ${this.name} for consistency checking. Falling back to ${fallback.name}.")
                    fallback
                }
                is ReasonerId.PureOwlApiReasoner -> this
                is ReasonerId.Openllet -> this
            }
        }
    private val syntacticModuleExtractionReasoner: OwlApiReasonerProvider
        get() = with(state.targetReasoner) {
            when (this) {
                is ReasonerId.PureJenaReasoner -> {
                    val fallback = ReasonerId.PureOwlApiReasoner.HermiT
                    logger.debug("Can not use ${this.name} for syntactic module extraction. Falling back to ${fallback.name}.")
                    fallback
                }
                is ReasonerId.PureOwlApiReasoner -> this
                is ReasonerId.Openllet -> this
            }
        }


    private fun getJenaModel(reasoner: ReasonerId): Model =
        reasoner.inferJenaModel(ontology)

    fun buildSparqlExecution(query: Query, model: Model): QueryExecution =
        sparqlReasoner.buildSparqlExecution(query, model)

    fun getSparqlModel(customBaseOntology: Ontology? = null): Model =
        sparqlReasoner.inferJenaModel(customBaseOntology ?: ontology)

    fun getShaclModel(): Model {
        val reasonerId = shaclReasoner

        // For SHACL, we have to infer all triples beforehand...
        return when (reasonerId) {
            is ReasonerId.PureJenaReasoner -> {
                val reasoner = reasonerId.getJenaReasoner()

                if (reasoner is GenericRuleReasoner) {
                    // No pure forward reasoning :/
                    // https://lists.apache.org/thread/7xsvbvlhyydnk0tcsntwbtsdcvw9q4rx
                    reasoner.setMode(GenericRuleReasoner.HYBRID)
                }

                val baseModel = ontology.asGraphModel()
                val infModel = baseModel.getInferenceModel(reasoner)
                infModel.prepare()

                infModel
            }

            else -> reasonerId.inferJenaModel(ontology)
        }
    }

    fun getTripleListingModel(): Model = getJenaModel(tripleListingReasoner)
    fun getJenaValidationModel(): InfModel =
        jenaValidationReasoner.inferJenaModel(ontology)

    private fun getOwlApiReasoner(reasonerId: OwlApiReasonerProvider, baseOntology: Ontology): OWLReasoner =
        reasonerId.getOwlApiReasoner(baseOntology)

    fun getConsistencyReasoner(): OWLReasoner = getOwlApiReasoner(consistencyReasoner, ontology)
    fun getOwlClassExpressionReasoner(baseOntology: Ontology): OWLReasoner =
        getOwlApiReasoner(owlClassExpressionReasoner, baseOntology)

    fun getSyntacticModuleExtractionReasoner(): OWLReasoner =
        getOwlApiReasoner(syntacticModuleExtractionReasoner, ontology)

    val prefixNameToUri: Map<String, String>

    init {
        val prefixNameToUri = mutableMapOf(
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
            prefixNameToUri["domain"] = domainURI
        }

        this.prefixNameToUri = prefixNameToUri
    }

    val uriToPrefixName: Map<String, String> = prefixNameToUri
        .entries
        .associate { (prefixName, prefix) -> prefix to prefixName }

    private val variableStore: MutableMap<String, RDFNode> = mutableMapOf()
    val variables: Set<String>
        get() = variableStore.keys

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