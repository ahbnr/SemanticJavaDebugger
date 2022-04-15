@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import de.ahbnr.semanticweb.jdi2owl.mapping.OntIRIs
import de.ahbnr.semanticweb.sjdb.utils.expandResourceToModel
import de.ahbnr.semanticweb.sjdb.utils.toPrettyString
import org.apache.jena.rdf.model.*
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFFormat
import org.apache.jena.riot.RDFWriter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class InspectCommand : REPLCommand(name = "inspect"), KoinComponent {
    private val URIs: OntIRIs by inject()
    private val variableOrIRI: String by argument()

    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        val rdfGraph = knowledgeBase.ontology.asGraphModel()

        val storedNodes = knowledgeBase.resolveVariableOrUri(variableOrIRI)
        if (storedNodes.isEmpty()) {
            logger.error("No node is known under this name.")
            throw ProgramResult(-1)
        }

        for ((identifier, storedNode) in storedNodes) {
            logger.debug("$identifier:")

            // the stored node might be part of a more complex inference / reasoner model.
            // E.g. it might be the result of a Openllet SPARQL-DL query.
            // Inspecting such nodes can take a long time, hence we try to use the node from the plain model
            // instead, where possible:
            val node: RDFNode = storedNode.visitWith(object : RDFVisitor {
                override fun visitLiteral(l: Literal): RDFNode = storedNode
                override fun visitBlank(r: Resource, id: AnonId): RDFNode = storedNode
                override fun visitURI(r: Resource, uri: String): RDFNode =
                    if (rdfGraph.containsResource(ResourceFactory.createResource(uri))) {
                        rdfGraph.getResource(uri)
                    } else {
                        storedNode
                    }
            }) as RDFNode

            when {
                node.isResource -> {
                    val resource = node.asResource()
                    RDFWriter.create()
                        .lang(Lang.TURTLE)
                        .format(RDFFormat.TURTLE_PRETTY)
                        .source(expandResourceToModel(resource, URIs.ns))
                        .output(logger.logStream())
                    // for (property in resource.listProperties()) {
                    //     logger.log(property.toPrettyString(model))
                    // }
                }
                else -> {
                    logger.log(node.toPrettyString(rdfGraph))
                }
            }

            logger.log("")
        }
    }
}