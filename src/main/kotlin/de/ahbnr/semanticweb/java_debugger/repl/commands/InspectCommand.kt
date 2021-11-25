@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.Namespaces
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import de.ahbnr.semanticweb.java_debugger.utils.expandResourceToModel
import de.ahbnr.semanticweb.java_debugger.utils.toPrettyString
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class InspectCommand(val ns: Namespaces) : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "inspect"

    private val usage = """
        Usage: inspect <variable name>
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val variableName = argv.firstOrNull()
        if (variableName == null) {
            logger.error(usage)
            return false
        }

        val ontology = repl.knowledgeBase
        if (ontology == null) {
            logger.error("You must first extract a knowledge base. Run buildkb.")
            return false
        }

        val model = ontology.asGraphModel()

        val node = repl.namedNodes.getOrDefault(variableName, null)
        if (node == null) {
            logger.error("No node is known under this name.")
            return false
        }

        when {
            node.isResource -> {
                val resource = node.asResource()
                RDFDataMgr.write(logger.logStream(), expandResourceToModel(resource, ns), Lang.TTL)
                // for (property in resource.listProperties()) {
                //     logger.log(property.toPrettyString(model))
                // }
            }
            else -> {
                logger.log(node.toPrettyString(model))
            }
        }

        logger.log("")

        return true
    }
}