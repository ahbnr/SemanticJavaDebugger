@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class ShaclCommand(
    private val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    override val name = "shacl"

    private val usage = """
        Usage: shacl <shapes file>4
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val shapesFile = argv.firstOrNull()
        if (shapesFile == null) {
            logger.error(usage)
            return false
        }

        val knowledgeBase = repl.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            return false
        }

        val model = knowledgeBase.getShaclModel()
        val graph = model.graph

        val shapesGraph = RDFDataMgr.loadGraph(shapesFile)
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

        return true
    }
}