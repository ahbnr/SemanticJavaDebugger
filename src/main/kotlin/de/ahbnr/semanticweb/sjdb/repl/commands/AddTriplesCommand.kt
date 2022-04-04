@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import de.ahbnr.semanticweb.sjdb.rdf.linting.LinterMode
import de.ahbnr.semanticweb.sjdb.rdf.linting.ModelSanityChecker
import de.ahbnr.semanticweb.sjdb.rdf.mapping.forward.utils.UniversalKnowledgeBaseParser
import org.koin.core.component.KoinComponent

class AddTriplesCommand : REPLCommand(name = "add-triples"), KoinComponent {
    val triplesString: String by argument()

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        val prefixes = knowledgeBase
            .prefixNameToUri
            .entries
            .joinToString("\n") { (prefixName, prefixUri) -> "PREFIX $prefixName: <$prefixUri>" }

        val triplesString = """
                $prefixes
                $triplesString
        """.trimIndent()

        val reader = UniversalKnowledgeBaseParser(
            knowledgeBase.ontology.asGraphModel(),
            "triples.ttl",
            triplesString.byteInputStream()
        )
        reader.readIntoModel()

        ModelSanityChecker().fullCheck(knowledgeBase.ontology, knowledgeBase.buildParameters.limiter, LinterMode.Normal)
    }
}