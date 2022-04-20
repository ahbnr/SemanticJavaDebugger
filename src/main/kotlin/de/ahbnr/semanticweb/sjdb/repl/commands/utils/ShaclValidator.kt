package de.ahbnr.semanticweb.sjdb.repl.commands.utils

import de.ahbnr.semanticweb.jdi2owl.Logger
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class ShaclValidator(
    private val knowledgeBase: KnowledgeBase,
    private val dontUseReasoner: Boolean,
    private val quiet: Boolean
) : KoinComponent {
    private val logger: Logger by inject()

    fun validate(
        shapesFile: File,
        createFocusVariables: Boolean
    ): Boolean {
        val model = knowledgeBase.getShaclModel(dontUseReasoner = dontUseReasoner)
        val graph = model.graph

        val shapesGraph = RDFDataMgr.loadGraph(shapesFile.path)
        val shapes = Shapes.parse(shapesGraph)

        val report = ShaclValidator.get().validate(shapes, graph)

        val conforms = report.conforms()

        if (conforms) {
            if (!quiet)
                logger.success("Conforms.")
        } else {
            if (!quiet) {
                logger.error("Does not conform!")
                logger.log("")
                logger.log("Report:")
                RDFDataMgr.write(logger.logStream(), report.model, Lang.TTL)
                logger.log("")
            }

            if (createFocusVariables) {
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

                if (!quiet) {
                    logger.log("The focus nodes have been made available under the following names: ")
                    logger.log(nameMap.keys.joinToString(", "))
                }
            }
        }

        return conforms
    }
}
