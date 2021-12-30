@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.utils.expandResourceToModel
import de.ahbnr.semanticweb.java_debugger.utils.toPrettyString
import org.apache.jena.rdf.model.*
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class InspectCommand() : REPLCommand(name = "inspect"), KoinComponent {
    val logger: Logger by inject()
    val URIs: OntURIs by inject()

    val variableOrIRI: String by argument()

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("You must first extract a knowledge base. Run buildkb.")
            throw ProgramResult(-1)
        }

        val model = knowledgeBase.ontology.asGraphModel()

        val storedNode = knowledgeBase.resolveVariableOrUri(variableOrIRI)
        if (storedNode == null) {
            logger.error("No node is known under this name.")
            throw ProgramResult(-1)
        }

        // the stored node might be part of a more complex inference / reasoner model.
        // E.g. it might be the result of a Openllet SPARQL-DL query.
        // Inspecting such nodes can take a long time, hence we try to use the node from the plain model
        // instead, where possible:
        val node: RDFNode = storedNode.visitWith(object : RDFVisitor {
            override fun visitLiteral(l: Literal): RDFNode = storedNode
            override fun visitBlank(r: Resource, id: AnonId): RDFNode = storedNode
            override fun visitURI(r: Resource, uri: String): RDFNode =
                if (model.containsResource(ResourceFactory.createResource(uri))) {
                    model.getResource(uri)
                } else {
                    storedNode
                }
        }) as RDFNode

        when {
            node.isResource -> {
                val resource = node.asResource()
                RDFDataMgr.write(logger.logStream(), expandResourceToModel(resource, URIs.ns), Lang.TTL)
                // for (property in resource.listProperties()) {
                //     logger.log(property.toPrettyString(model))
                // }
            }
            else -> {
                logger.log(node.toPrettyString(model))
            }
        }

        logger.log("")
    }
}