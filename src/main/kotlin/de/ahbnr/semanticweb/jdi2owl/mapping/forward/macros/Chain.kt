package de.ahbnr.semanticweb.jdi2owl.mapping.forward.macros

import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.OntURIs
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.RDFList
import org.apache.jena.rdf.model.Resource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class Chain : Macro, KoinComponent {
    private val URIs: OntURIs by inject()
    private val logger: Logger by inject()

    private class ChainCall(
        val chainTarget: Property,
        val chainTargetType: Resource?, // FIXME: Can the same IRI refer to a data property and an object property? (Punning?)
        val chain: List<Property>,
        val chainType: Resource
    )

    private inner class ChainExecutor(private val rdfGraph: Model) {
        private val rdfTypeProperty = rdfGraph.getProperty(URIs.rdf.type)
        private val datatypeProperty = rdfGraph.getResource(URIs.owl.DatatypeProperty)
        private val objectProperty = rdfGraph.getResource(URIs.owl.ObjectProperty)

        private fun findPropertyType(property: Resource): Resource? =
            if (property.hasProperty(rdfTypeProperty, datatypeProperty))
                datatypeProperty
            else if (property.hasProperty(rdfTypeProperty, objectProperty))
                objectProperty
            else null

        fun findChains() = sequence {
            val chainsPropertiesProperty = rdfGraph.getProperty(URIs.macros.chainsProperties)

            targetLoop@ for (chainTarget in rdfGraph.listSubjectsWithProperty(chainsPropertiesProperty)) {
                if (!chainTarget.isURIResource) {
                    logger.error("The chain macro was invoked for $chainTarget but it must be a resource with an IRI.")
                    continue
                }

                val chainsPropertiesStmts = chainTarget.listProperties(chainsPropertiesProperty)

                if (!chainsPropertiesStmts.hasNext()) {
                    logger.error("The chain macro was invoked for $chainTarget but no list of properties was declared via $chainsPropertiesProperty.")
                    continue
                }

                val chainsPropertiesStmt = chainsPropertiesStmts.nextStatement()
                if (chainsPropertiesStmts.hasNext()) {
                    logger.error("Multiple instances of the $chainsPropertiesProperty property have been declared for applying the chain macro to $chainTarget.\nAll but the first declaration will be ignored.")
                }

                if (!chainsPropertiesStmt.`object`.canAs(RDFList::class.java)) {
                    logger.error("The chain macro was invoked for $chainTarget but its $chainsPropertiesProperty property does not link it to an RDF list.")
                    continue
                }

                val rawChainList = chainsPropertiesStmt
                    .`object`
                    .`as`(RDFList::class.java)

                val propertiesToBeChained = ArrayList<Property>(rawChainList.size())

                for ((idx, propertyNode) in rawChainList.iterator().withIndex()) {
                    if (!propertyNode.isURIResource) {
                        logger.error("The chain macro was invoked for $chainTarget but its $chainsPropertiesProperty property contains a node that is not an IRI resource: $propertyNode")
                        continue@targetLoop
                    }

                    val propertyResource = propertyNode.asResource()
                    val propertyType = findPropertyType(propertyResource)

                    if (propertyType != null && propertyType.uri == URIs.owl.DatatypeProperty && idx != rawChainList.size() - 1
                    ) {
                        logger.error("The chain macro was invoked for $chainTarget. Only the last property of a chain can be a OWL DatatypeProperty, but $propertyNode is not listed last.")
                        continue@targetLoop
                    }

                    propertiesToBeChained.add(
                        rdfGraph.getProperty(propertyResource.uri)
                    )
                }

                val last = propertiesToBeChained.lastOrNull()
                if (last == null) {
                    logger.error("The chain macro was invoked for $chainTarget but the chain is empty!")
                    continue
                }

                val chainTargetType = findPropertyType(chainTarget)
                val chainType = findPropertyType(last)
                    ?: chainTargetType // If no type is declared for the last element in the chain, we just assume its the same type as the target
                    ?: throw RuntimeException("Could not determine property type of chain. Either the chain target or the last link of the chain must be declared as either being an OWL ObjectProperty or DataProperty.")

                if (chainTargetType != null && chainType.uri != chainTargetType.uri) {
                    logger.error("The chain macro was invoked for $chainTarget. However, the property type of the chain ($chainType) and the type of the target property ($chainTargetType) do not match!")
                    continue
                }

                yield(
                    ChainCall(
                        rdfGraph.getProperty(chainTarget.uri),
                        chainTargetType,
                        propertiesToBeChained,
                        chainType
                    )
                )
            }
        }

        private fun findChainImages(
            domainElement: Resource,
            chain: List<Property>,
            chainTargetNode: Resource
        ): Sequence<Resource> = sequence {
            val currentChainLink = chain.firstOrNull()

            if (currentChainLink == null) {
                yield(domainElement)
                return@sequence
            }

            for (image in rdfGraph.listObjectsOfProperty(domainElement, currentChainLink)) {
                if (!image.isResource) {
                    logger.error("The chain macro was invoked for $chainTargetNode. Encountered non-resource element in the range of $currentChainLink along the chain: $image.")
                    continue
                }

                yieldAll(findChainImages(image.asResource(), chain.drop(1), chainTargetNode))
            }
        }

        private fun findChainedPairs(chainCall: ChainCall) = sequence<Pair<Resource, Resource>> {
            val firstChainLink = chainCall.chain.first()
            val domain = rdfGraph.listSubjectsWithProperty(firstChainLink)

            for (domainElement in domain) {
                for (image in findChainImages(domainElement, chainCall.chain, chainCall.chainTarget)) {
                    yield(Pair(domainElement, image))
                }
            }
        }

        fun executeChainCall(chainCall: ChainCall) {
            if (chainCall.chainTargetType == null) {
                logger.warning("The chain macro was invoked for ${chainCall.chainTarget}. Since it has not been declared to be an OWL property, we now add a declaration to make it a ${chainCall.chainType}.")
                chainCall.chainTarget.addProperty(rdfTypeProperty, chainCall.chainType)
            }

            for ((domainElement, imageElement) in findChainedPairs(chainCall)) {
                rdfGraph.add(domainElement, chainCall.chainTarget, imageElement)
            }
        }
    }

    override fun executeAll(rdfGraph: Model) {
        val executor = ChainExecutor(rdfGraph)

        for (chainCall in executor.findChains()) {
            executor.executeChainCall(chainCall)
        }
    }
}