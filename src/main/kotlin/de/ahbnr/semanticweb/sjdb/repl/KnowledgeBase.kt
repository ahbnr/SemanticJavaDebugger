package de.ahbnr.semanticweb.sjdb.repl

import com.github.owlcs.ontapi.OntManagers
import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.OntURIs
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.BuildParameters
import org.apache.jena.query.Query
import org.apache.jena.query.QueryExecution
import org.apache.jena.rdf.model.InfModel
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owl.explanation.api.Explanation
import org.semanticweb.owl.explanation.impl.blackbox.*
import org.semanticweb.owl.explanation.impl.blackbox.checker.BlackBoxExplanationGeneratorFactory
import org.semanticweb.owl.explanation.impl.blackbox.checker.SatisfiabilityEntailmentCheckerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.OWLAxiom
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.reasoner.*


class KnowledgeBase(
    val ontology: Ontology,
    val buildParameters: BuildParameters
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

    private fun getOwlApiReasoner(reasonerId: OwlApiReasonerProvider, baseOntology: Ontology): CloseableOWLReasoner =
        reasonerId.getOwlApiReasoner(
            baseOntology,
            if (state.logReasoner)
                SimpleConfiguration(object : ReasonerProgressMonitor {
                    override fun reasonerTaskStarted(taskName: String) {
                        logger.debug("Reasoner: Started task \"$taskName\".")
                    }
                })
            else null
        ).asCloseable()

    // FIXME: This code is a mess. Clean up, as soon as we debugged the reasoner inconsitencies for domain:ConditionThree of the BTree example
    fun getExplanationGenerator(
        baseOntology: OWLOntology,
        reasoner: OWLReasoner? = null
    ): (OWLAxiom) -> Set<Explanation<OWLAxiom>> {
        val reasonerFactory = owlClassExpressionReasoner.getOwlApiReasonerFactory()
        // val generatorFactory = ExplanationManager.createExplanationGeneratorFactory(
        //     reasonerFactory,
        //     OntManagers::createManager // for version 5.0.0 of owlexplanation
        // )

        var origFreshEntityPolicy: FreshEntityPolicy? = null
        var origIndividualNodeSetPolicy: IndividualNodeSetPolicy? = null
        var origReasonerProgressMonitor: ReasonerProgressMonitor? = null
        var origIndividualTaskTimeout: Long? = null
        val patchedReasonerFactory = if (reasonerFactory is org.semanticweb.HermiT.ReasonerFactory) {
            object : OWLReasonerFactory by reasonerFactory {
                override fun createReasoner(ontology: OWLOntology, config: OWLReasonerConfiguration): OWLReasoner {
                    return if (reasoner == null) {
                        val hermitConfig = org.semanticweb.HermiT.Configuration()

                        hermitConfig.freshEntityPolicy = config.freshEntityPolicy
                        hermitConfig.individualNodeSetPolicy = config.individualNodeSetPolicy
                        hermitConfig.reasonerProgressMonitor = config.progressMonitor
                        hermitConfig.individualTaskTimeout = config.timeOut

                        // TODO: Necessary due to anySimpleType definition.
                        //   Fix this.
                        // Maybe set at least a warning monitor instead
                        // hermitConfig.warningMonitor = Configuration.WarningMonitor { logger.warning(it) }
                        hermitConfig.ignoreUnsupportedDatatypes = true

                        reasonerFactory.createReasoner(baseOntology, hermitConfig)
                    } else {
                        if (reasoner is org.semanticweb.HermiT.Reasoner) {
                            origFreshEntityPolicy = reasoner.configuration.freshEntityPolicy
                            origIndividualNodeSetPolicy = reasoner.configuration.individualNodeSetPolicy
                            origReasonerProgressMonitor = reasoner.configuration.reasonerProgressMonitor
                            origIndividualTaskTimeout = reasoner.configuration.individualTaskTimeout

                            reasoner.configuration.freshEntityPolicy = config.freshEntityPolicy
                            reasoner.configuration.individualNodeSetPolicy = config.individualNodeSetPolicy
                            reasoner.configuration.reasonerProgressMonitor = config.progressMonitor
                            reasoner.configuration.individualTaskTimeout = config.timeOut
                        }
                        reasoner
                    }
                }
            }
        } else reasonerFactory

        // We need to construct the explanation generator factory manually since there is a NullPointerException bug
        // in version 5.0.0
        val m = if (baseOntology is Ontology)
            OntManagers::createManager
        else OWLManager::createOWLOntologyManager

        val checkerFactory: EntailmentCheckerFactory<OWLAxiom> =
            SatisfiabilityEntailmentCheckerFactory(
                patchedReasonerFactory,
                m
            )
        val config = Configuration(
            checkerFactory,
            StructuralTypePriorityExpansionStrategy(
                InitialEntailmentCheckStrategy.PERFORM,
                m
            ),
            DivideAndConquerContractionStrategy(),
            m
        )
        val generatorFactory = BlackBoxExplanationGeneratorFactory(config)

        return { axiom ->
            val explanationGenerator = generatorFactory.createExplanationGenerator(baseOntology)
            val result = explanationGenerator.getExplanations(axiom, 1)

            if (reasoner is org.semanticweb.HermiT.Reasoner) {
                reasoner.configuration.freshEntityPolicy = origFreshEntityPolicy
                reasoner.configuration.individualNodeSetPolicy = origIndividualNodeSetPolicy
                reasoner.configuration.reasonerProgressMonitor = origReasonerProgressMonitor
                reasoner.configuration.individualTaskTimeout = origIndividualTaskTimeout!!
            }

            result
        }
    }


    fun getConsistencyReasoner(): CloseableOWLReasoner = getOwlApiReasoner(consistencyReasoner, ontology)
    fun getOwlClassExpressionReasoner(baseOntology: Ontology): CloseableOWLReasoner =
        getOwlApiReasoner(owlClassExpressionReasoner, baseOntology)

    fun getSyntacticModuleExtractionReasoner(): CloseableOWLReasoner =
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
            "local" to URIs.ns.local,
        )

        prefixNameToUri.putAll(ontology.asGraphModel().nsPrefixMap)

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

        if (name.contains('*')) {
            throw IllegalArgumentException("A variable name may not contain an asterisk: \"*\"")
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

    fun asPrefixNameUri(uri: String): String {
        val prefixNameAndUri =
            uriToPrefixName.entries.find { (prefixUri, _) -> uri.startsWith(prefixUri) }

        return if (prefixNameAndUri != null) {
            val (prefixUri, prefixName) = prefixNameAndUri

            uri.replaceRange(prefixUri.indices, "$prefixName:")
        } else uri
    }

    fun resolvePrefixNameInUri(uri: String): String {
        val prefixNameAndUri =
            prefixNameToUri.entries.find { (prefixName, _) -> uri.startsWith("$prefixName:") }

        return if (prefixNameAndUri != null) {
            val (prefixName, prefixUri) = prefixNameAndUri

            uri.replaceRange(0 until prefixName.length + 1, prefixUri)
        } else uri
    }

    fun resolveVariableOrUri(variableOrUri: String): List<Pair<String, RDFNode>> =
        // Its a variable if it starts with a '?'
        if (variableOrUri.startsWith("?")) {
            // Resolve wildcards, if necessary
            if (variableOrUri.contains('*')) {
                val wildcardRegex = Regex("\\" + variableOrUri.replace("*", ".*"))
                variableStore
                    .filter { (variable, _) -> wildcardRegex.matches(variable) }
                    .map { (variable, node) -> Pair(variable, node) }
            } else {
                val maybeResource = variableStore.getOrDefault(variableOrUri, null)

                if (maybeResource != null) listOf(Pair(variableOrUri, maybeResource)) else emptyList()
            }
        } else {
            // We treat it as an IRI otherwise
            val rdfGraph = ontology.asGraphModel()
            val resource = rdfGraph.getResource(resolvePrefixNameInUri(variableOrUri))

            if (rdfGraph.containsResource(resource))
                listOf(Pair(variableOrUri, resource))
            else emptyList()
        }
}