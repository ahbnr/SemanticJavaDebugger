@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File


class ShaclCommand(
    private val graphGenerator: GraphGenerator
) : REPLCommand(name = "shacl"), KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    private val shapesFile: File by argument().file(mustExist = true, mustBeReadable = true)

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        val model = knowledgeBase.getShaclModel()
        val graph = model.graph

        val shapesGraph = RDFDataMgr.loadGraph(shapesFile.path)
        val shapes = Shapes.parse(shapesGraph)

        val report = ShaclValidator.get().validate(shapes, graph)

        if (report.conforms()) {
            logger.success("Conforms.")
        } else {
            logger.error("Does not conform!")
            logger.log("")
            logger.log("Report:")
            RDFDataMgr.write(logger.logStream(), report.model, Lang.TTL)
            logger.log("")

            knowledgeBase
                .variables
                .filter { it.startsWith("?focus") }
                .forEach { knowledgeBase.removeVariable(it) }

            val nameMap = mutableMapOf<String, RDFNode>()
            report.entries.forEachIndexed { idx, entry ->
                val rdfNode = model.asRDFNode(entry.focusNode())

                val name = "?focus$idx"

                nameMap[name] = rdfNode
            }
            nameMap.forEach { (name, value) -> knowledgeBase.setVariable(name, value) }

            logger.log("The focus nodes have been made available under the following names: ")
            logger.log(nameMap.keys.joinToString(", "))
        }
    }
}