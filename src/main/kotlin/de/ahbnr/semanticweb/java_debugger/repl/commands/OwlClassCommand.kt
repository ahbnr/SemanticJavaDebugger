@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxParserImpl
import org.semanticweb.owlapi.manchestersyntax.renderer.ParserException
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException
import java.io.PrintStream
import kotlin.streams.asSequence

class OwlClassCommand : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    override val name = "owlclass"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val knowledgeBase = repl.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            return false
        }

        val manchesterParser = ManchesterOWLSyntaxParserImpl(
            knowledgeBase.ontology.owlOntologyManager.ontologyConfigurator,
            knowledgeBase.ontology.owlOntologyManager.owlDataFactory
        )
        for ((prefixName, prefixUri) in knowledgeBase.prefixNameToUri) {
            manchesterParser.prefixManager.setPrefix(prefixName, prefixUri)
        }
        manchesterParser.setDefaultOntology(knowledgeBase.ontology)

        val classExpression = try {
            manchesterParser.parseClassExpression(rawInput)
        } catch (e: ParserException) {
            val printStream = PrintStream(logger.logStream())
            e.printStackTrace(printStream)
            printStream.flush()
            logger.error("Could not parse Manchester class expression.")
            return false
        }

        val reasoner = knowledgeBase.getOwlClassExpressionReasoner()

        val instances = try {
            reasoner.getInstances(classExpression)
        } catch (e: InconsistentOntologyException) {
            val printStream = PrintStream(logger.logStream())
            e.printStackTrace(printStream)
            printStream.flush()
            logger.error("The ontology is not consistent.")

            return false
        }

        if (instances.isEmpty) {
            logger.log("Found no instances for this class.")
        } else {
            for (instance in instances) {
                logger.log(
                    instance
                        .entities()
                        .asSequence()
                        .joinToString(", ") {
                            val prefixUriAndName =
                                knowledgeBase.uriToPrefixName.entries.find { (uri, prefixName) ->
                                    it.iri.startsWith(uri)
                                }
                            if (prefixUriAndName != null) {
                                val (prefixUri, prefixName) = prefixUriAndName

                                it.iri.replaceRange(prefixUri.indices, "$prefixName:")
                            } else it.iri
                        }
                )
            }
        }

        return true
    }
}