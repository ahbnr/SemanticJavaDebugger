@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFFormat
import org.apache.jena.riot.RDFWriter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DumpKBCommand : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "dumpkb"

    private val usage = """
        Usage: dumpkb <file>
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val knowledgeBase = repl.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base is available. Run `buildkb` first.")
            return false
        }

        val file = argv.firstOrNull()
        if (file == null) {
            logger.error(usage)
            return false
        }

        RDFWriter
            .create(knowledgeBase.ontology.asGraphModel())
            .lang(Lang.TURTLE)
            .format(RDFFormat.TURTLE_PRETTY)
            .output(file)
        logger.success("Knowledge base saved to $file.")

        return true
    }
}