@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class DumpKBCommand : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "dumpkb"

    private val usage = """
        Usage: dumpkb <file>
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        val ontology = repl.knowledgeBase
        if (ontology == null) {
            logger.error("No knowledge base is available. Run `buildkb` first.")
            return
        }

        val file = argv.firstOrNull()
        if (file == null) {
            logger.error(usage)
            return
        }

        File(file).outputStream().use { stream ->
            RDFDataMgr.write(stream, ontology.asGraphModel(), Lang.TTL)
            logger.success("Knowledge base saved to $file.")
        }
    }
}