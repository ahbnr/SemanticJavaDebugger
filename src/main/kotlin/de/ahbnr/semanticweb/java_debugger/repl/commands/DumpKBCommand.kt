@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFFormat
import org.apache.jena.riot.RDFWriter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class DumpKBCommand : REPLCommand(name = "dumpkb"), KoinComponent {
    val logger: Logger by inject()

    val file: File by argument().file(mustBeWritable = true)

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base is available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        RDFWriter
            .create(knowledgeBase.ontology.asGraphModel())
            .lang(Lang.TURTLE)
            .format(RDFFormat.TURTLE_PRETTY)
            .output(file.outputStream())
        logger.success("Knowledge base saved to $file.")
    }
}