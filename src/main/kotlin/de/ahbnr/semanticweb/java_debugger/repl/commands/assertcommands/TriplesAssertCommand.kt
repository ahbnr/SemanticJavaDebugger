@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands.assertcommands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import de.ahbnr.semanticweb.java_debugger.repl.commands.REPLCommand
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.SimpleSelector
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.riot.RDFParser
import org.koin.core.component.KoinComponent

class TriplesAssertCommand : REPLCommand(name = "triples"), KoinComponent {
    val triples: String by argument()

    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        val model = knowledgeBase.getTripleListingModel()

        val prefixes = knowledgeBase
            .prefixNameToUri
            .entries
            .joinToString("\n") { (prefixName, prefixUri) -> "PREFIX $prefixName: <$prefixUri>" }

        val targetModel = ModelFactory.createDefaultModel()

        RDFParser
            .create()
            .source(
                """
                    $prefixes
                    $triples
                """
                    .trimIndent()
                    .byteInputStream()
            )
            .lang(RDFLanguages.TTL)
            .parse(targetModel)

        for (statement in targetModel.listStatements()) {
            if (statement.subject.isAnon || statement.`object`.isAnon) {
                logger.error("Blank nodes are not supported here.")
                throw ProgramResult(-1)
            }

            val selector = SimpleSelector(statement.subject, statement.predicate, statement.`object`)

            if (model.query(selector).isEmpty) {
                logger.error("FAILED!")
                logger.log("Triple not found: $statement.")
                throw ProgramResult(-1)
            }
        }

        logger.success("PASSED")
    }
}