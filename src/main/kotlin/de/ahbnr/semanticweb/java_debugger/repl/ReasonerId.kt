package de.ahbnr.semanticweb.java_debugger.repl

import com.github.owlcs.ontapi.OntManagers
import com.github.owlcs.ontapi.Ontology
import org.apache.jena.rdf.model.InfModel
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.reasoner.Reasoner
import org.apache.jena.reasoner.ReasonerRegistry
import org.semanticweb.HermiT.ReasonerFactory
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.parameters.OntologyCopy
import org.semanticweb.owlapi.reasoner.OWLReasoner
import org.semanticweb.owlapi.util.InferredOntologyGenerator
import uk.ac.manchester.cs.jfact.JFactFactory

sealed class ReasonerId(val name: String) {
    sealed class JenaReasoner(name: String) : ReasonerId(name) {
        object JenaOwlMicro : JenaReasoner("JenaOwlMicro") {
            override fun getReasoner(): Reasoner = ReasonerRegistry.getOWLMicroReasoner()
        }

        object JenaOwlMini : JenaReasoner("JenaOwlMini") {
            override fun getReasoner(): Reasoner = ReasonerRegistry.getOWLMiniReasoner()
        }

        object JenaOwl : JenaReasoner("JenaOwl") {
            override fun getReasoner(): Reasoner = ReasonerRegistry.getOWLReasoner()
        }

        abstract fun getReasoner(): Reasoner

        override fun inferJenaModel(baseOntology: Ontology): InfModel {
            val baseModel = baseOntology.asGraphModel()
            val reasoner = this.getReasoner()

            return ModelFactory.createInfModel(reasoner, baseModel)
        }
    }

    sealed class OwlApiReasoner(name: String) : ReasonerId(name) {
        object HermiT : OwlApiReasoner("HermiT") {
            override fun getReasoner(baseOntology: OWLOntology): OWLReasoner =
                ReasonerFactory().createReasoner(baseOntology)
        }

        object JFact : OwlApiReasoner("JFact") {
            override fun getReasoner(baseOntology: OWLOntology): OWLReasoner =
                JFactFactory().createReasoner(baseOntology)
        }

        abstract fun getReasoner(baseOntology: OWLOntology): OWLReasoner

        override fun inferJenaModel(baseOntology: Ontology): Model {
            val manager = OntManagers.createManager()
            val ontologyCopy = manager.copyOntology(baseOntology, OntologyCopy.DEEP)

            val reasoner = this.getReasoner(ontologyCopy)

            val dataFactory = manager.owlDataFactory
            val inferenceGenerator = InferredOntologyGenerator(reasoner)

            // perform all possible inferences
            // (This can probably be implemented much more efficiently by adapting Jenas reasoner interface)
            inferenceGenerator.fillOntology(dataFactory, ontologyCopy)

            return ontologyCopy.asGraphModel()
        }
    }

    abstract fun inferJenaModel(baseOntology: Ontology): Model

    companion object {
        val availableReasoners = listOf(
            JenaReasoner.JenaOwlMicro,
            JenaReasoner.JenaOwlMini,
            JenaReasoner.JenaOwl,
            OwlApiReasoner.HermiT,
            OwlApiReasoner.JFact
        )
    }
}