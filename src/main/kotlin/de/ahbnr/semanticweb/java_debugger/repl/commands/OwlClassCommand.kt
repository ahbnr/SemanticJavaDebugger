@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.optimization.extractSyntacticLocalityModule
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

    private val nonOptionRegex = """\s[^-\s]""".toRegex()

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

        var options: List<String> = emptyList()
        var rawExpression: String = rawInput
        if (rawInput.startsWith(' ') || rawInput.startsWith('-')) {
            val nonOptionPosition = nonOptionRegex.find(rawInput)
            if (nonOptionPosition == null) {
                logger.error("No expression has been specified.")
                return false
            }
            val startOfExpression = nonOptionPosition.range.first
            options = rawInput.substring(0 until startOfExpression).split(' ')
            rawExpression = rawInput.substring(startOfExpression)
        }

        val classExpression = try {
            manchesterParser.parseClassExpression(rawExpression)
        } catch (e: ParserException) {
            val printStream = PrintStream(logger.logStream())
            e.printStackTrace(printStream)
            printStream.flush()
            logger.error("Could not parse Manchester class expression.")
            return false
        }

        val ontology = if (options.contains("--optimize")) {
            logger.debug("Axioms before module extraction: ${knowledgeBase.ontology.axiomCount}.")
            val module = extractSyntacticLocalityModule(
                knowledgeBase,
                classExpression.signature().asSequence().toSet(),
                -1
            )
            logger.debug("Axioms after module extraction: ${module.axiomCount}.")
            module
        } else knowledgeBase.ontology

        val reasoner = knowledgeBase.getOwlClassExpressionReasoner(ontology)

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