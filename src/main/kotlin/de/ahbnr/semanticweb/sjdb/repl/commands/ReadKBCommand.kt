@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.github.owlcs.ontapi.OntManagers
import de.ahbnr.semanticweb.sjdb.rdf.mapping.forward.utils.UniversalKnowledgeBaseParser
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import org.koin.core.component.KoinComponent

class ReadKBCommand : REPLCommand(name = "readkb"), KoinComponent {
    val kbfile by argument().file(
        mustExist = true,
        mustBeReadable = true
    )

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            logger.log("When reading a knowledge base, we always assume it refers to the currently loaded program and it replaces its knowledge base.")
            throw ProgramResult(-1)
        }

        val ontManager = OntManagers.createManager()
        val ontology = ontManager.createOntology()
        val model = ontology.asGraphModel()

        val reader = UniversalKnowledgeBaseParser(model, kbfile.name, kbfile.inputStream())
        reader.readIntoModel()

        val newKnowledgeBase = KnowledgeBase(
            ontology,
            knowledgeBase.buildParameters
        )

        state.knowledgeBase = newKnowledgeBase

        logger.success("Knowledge base loaded from file.")
        logger.debug("We assume it refers to the JVM and source currently being executed!")
    }
}