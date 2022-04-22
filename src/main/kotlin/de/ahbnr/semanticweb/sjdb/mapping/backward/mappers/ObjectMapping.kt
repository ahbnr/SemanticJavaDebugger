package de.ahbnr.semanticweb.sjdb.mapping.backward.mappers

import com.sun.jdi.ObjectReference
import de.ahbnr.semanticweb.jdi2owl.debugging.JvmState
import de.ahbnr.semanticweb.jdi2owl.mapping.MappingLimiter
import de.ahbnr.semanticweb.jdi2owl.mapping.OntIRIs
import de.ahbnr.semanticweb.jdi2owl.Logger
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import org.apache.jena.rdf.model.Literal
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.apibinding.OWLFunctionalSyntaxFactory
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.parameters.Imports
import kotlin.streams.asSequence

object ObjectMapping : KoinComponent {
    private val URIs: OntIRIs by inject()
    private val logger: Logger by inject()

    private class MappingContext(
        resource: Resource,
        val knowledgeBase: KnowledgeBase
    ) {
        private val rdfGraph = knowledgeBase.ontology.asGraphModel()
        private val objectResource: Resource

        val objectId: Long

        init {
            // The resource we try to reverse might not be the actual node a java object was mapped to,
            // but a different resource referencing the same individual.
            // So lets iterate over them until we find the right one
            objectResource = sameIndividuals(resource)
                .firstOrNull { isJavaObject(it) }
                ?: throw IllegalArgumentException("${resource.uri} nor other rdf nodes that refer to the same individual represent a Java object.")

            objectId = getObjectId(objectResource)
        }

        private fun sameIndividuals(resource: Resource) = sequence<Resource> {
            yield(resource)

            val individualRdfNode = knowledgeBase.ontology.asGraphModel().getIndividual(resource.uri)
                ?: throw IllegalArgumentException("${resource.uri} is not an OWL individual, so it can not possibly be the mapping of a Java object.")

            yieldAll(individualRdfNode.sameIndividuals().asSequence())

            // As a last resort, check if reasoner can find the mapping
            val inferredSameIndividuals = knowledgeBase
                .getOwlClassExpressionReasoner(knowledgeBase.ontology)
                .use { reasoner ->
                    val owlIndividual = OWLFunctionalSyntaxFactory.NamedIndividual(IRI.create(resource.uri))
                    if (!knowledgeBase.ontology.isDeclared(owlIndividual, Imports.INCLUDED))
                        throw IllegalArgumentException("${resource.uri} is not a named OWL individual.")

                    val allIndividuals = reasoner.sameIndividuals(owlIndividual)

                    allIndividuals
                        .map { rdfGraph.getResource(it.iri.iriString) }
                }

            yieldAll(inferredSameIndividuals.asSequence())
        }

        private fun isJavaObject(resource: Resource): Boolean =
            resource.hasProperty(
                rdfGraph.getProperty(URIs.rdf.type),
                rdfGraph.getResource(URIs.java.Object)
            )

        private fun getObjectId(resource: Resource): Long =
            (
                    resource.getProperty(
                        rdfGraph.getProperty(URIs.java.hasUniqueId)
                    )
                        ?.`object` as? Literal
                    )
                ?.lexicalForm
                ?.toLong()
                ?: throw IllegalArgumentException("The RDF graph is incomplete. ${resource.uri} does represent a Java object, but no object ID is associated with it: No ${URIs.java.hasUniqueId} property is present.")
    }


    fun map(
        node: RDFNode,
        knowledgeBase: KnowledgeBase,
        jvmState: JvmState,
        limiter: MappingLimiter
    ): ObjectReference? {
        val resource = node as? Resource ?: return null

        val context = try {
            MappingContext(resource, knowledgeBase)
        } catch (e: IllegalArgumentException) {
            logger.error(e.message ?: "The supplied RDF node does not represent a Java object.")
            return null
        }

        return jvmState.getObjectById(context.objectId)
    }
}
