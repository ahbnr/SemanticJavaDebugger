package de.ahbnr.semanticweb.java_debugger.repl

import com.github.owlcs.ontapi.OntManagers
import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import openllet.jena.PelletReasoner
import openllet.owlapi.OpenlletReasoner
import openllet.owlapi.OpenlletReasonerFactory
import openllet.query.sparqldl.jena.SparqlDLExecutionFactory
import org.apache.jena.query.Query
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.rdf.model.InfModel
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.reasoner.Reasoner
import org.apache.jena.reasoner.ReasonerRegistry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.HermiT.ReasonerFactory
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.parameters.OntologyCopy
import org.semanticweb.owlapi.reasoner.OWLReasoner
import org.semanticweb.owlapi.util.InferredOntologyGenerator
import uk.ac.manchester.cs.jfact.JFactFactory

interface JenaModelInferrer {
    fun inferJenaModel(baseOntology: Ontology): Model
}

interface JenaReasonerProvider : JenaModelInferrer {
    fun getJenaReasoner(): Reasoner
    override fun inferJenaModel(baseOntology: Ontology): InfModel
}

interface OwlApiReasonerProvider : JenaModelInferrer {
    fun getOwlApiReasoner(baseOntology: OWLOntology): OWLReasoner
}

sealed class ReasonerId(val name: String) : JenaModelInferrer {
    sealed class PureJenaReasoner(name: String) : ReasonerId(name), JenaReasonerProvider {
        object JenaOwlMicro : PureJenaReasoner("JenaOwlMicro") {
            override fun getJenaReasoner(): Reasoner = ReasonerRegistry.getOWLMicroReasoner()
        }

        object JenaOwlMini : PureJenaReasoner("JenaOwlMini") {
            override fun getJenaReasoner(): Reasoner = ReasonerRegistry.getOWLMiniReasoner()
        }

        object JenaOwl : PureJenaReasoner("JenaOwl") {
            override fun getJenaReasoner(): Reasoner = ReasonerRegistry.getOWLReasoner()
        }

        override fun inferJenaModel(baseOntology: Ontology): InfModel {
            val baseModel = baseOntology.asGraphModel()
            val reasoner = this.getJenaReasoner()

            return ModelFactory.createInfModel(reasoner, baseModel)
        }
    }

    sealed class PureOwlApiReasoner(name: String) : ReasonerId(name), OwlApiReasonerProvider {
        object HermiT : PureOwlApiReasoner("HermiT") {
            override fun getOwlApiReasoner(baseOntology: OWLOntology): OWLReasoner =
                ReasonerFactory().createReasoner(baseOntology)
        }

        object JFact : PureOwlApiReasoner("JFact") {
            override fun getOwlApiReasoner(baseOntology: OWLOntology): OWLReasoner =
                JFactFactory().createReasoner(baseOntology)
        }

        override fun inferJenaModel(baseOntology: Ontology): Model {
            val manager = OntManagers.createManager()
            val ontologyCopy = manager.copyOntology(baseOntology, OntologyCopy.DEEP)

            val reasoner = this.getOwlApiReasoner(ontologyCopy)

            val dataFactory = manager.owlDataFactory
            val inferenceGenerator = InferredOntologyGenerator(reasoner)

            // perform all possible inferences
            // (This can probably be implemented much more efficiently by adapting Jenas reasoner interface)
            inferenceGenerator.fillOntology(dataFactory, ontologyCopy)

            return ontologyCopy.asGraphModel()
        }
    }

    /**
     * Openllet is interesting for multiple reasons
     *
     * * directly supports Jena AND OWL-API
     * * supports SWRL
     * * supports SPARQL-DL
     *  * https://www.derivo.de/en/resources/sparql-dl-api/
     *  * http://webont.org/owled/2007/PapersPDF/submission_23.pdf
     */
    object Openllet : ReasonerId("Openllet"), JenaReasonerProvider, OwlApiReasonerProvider {
        override fun getOwlApiReasoner(baseOntology: OWLOntology): OpenlletReasoner {
            val reasoner = OpenlletReasonerFactory.getInstance().createReasoner(baseOntology)
            // TODO: Not sure if this is necessary
            reasoner.prepareReasoner()

            return reasoner
        }

        override fun getJenaReasoner(): PelletReasoner =
            openllet.jena.PelletReasonerFactory.theInstance().create()

        override fun inferJenaModel(baseOntology: Ontology): InfModel {
            val reasoner = getJenaReasoner()
            val baseModel = baseOntology.asGraphModel()

            // TODO: Not sure if using any of these alternatives makes a difference.
            //   It doesnt seem so: https://github.com/Galigator/openllet/blob/670227cb108a13fa0abc5249a625dc79b106ae4e/examples/src/main/java/openllet/examples/JenaReasoner.java#L23
            //
            // val ontModel = ModelFactory.createOntologyModel(PelletReasonerFactory.THE_SPEC, baseModel)
            // ontModel.setStrictMode(false)
            // return ontModel
            // return baseModel.getInferenceModel(reasoner)
            return ModelFactory.createInfModel(reasoner, baseModel)
        }

        override fun buildSparqlExecution(query: Query, model: Model): QueryExecution {
            // TODO: Test the performance and result differences between using
            //   a Openllet inference model with ARQ VS using the Openllet SPARQL-DL engine
            logger.debug("Using SPARQL-DL engine of Openllet instead of default Jena ARQ execution engine.")
            return SparqlDLExecutionFactory.create(query, model)
        }
    }

    open fun buildSparqlExecution(query: Query, model: Model): QueryExecution =
        QueryExecutionFactory.create(query, model)

    companion object : KoinComponent {
        protected val logger: Logger by inject()

        val availableReasoners = listOf(
            PureJenaReasoner.JenaOwlMicro,
            PureJenaReasoner.JenaOwlMini,
            PureJenaReasoner.JenaOwl,
            PureOwlApiReasoner.HermiT,
            PureOwlApiReasoner.JFact,
            Openllet
        )
    }
}