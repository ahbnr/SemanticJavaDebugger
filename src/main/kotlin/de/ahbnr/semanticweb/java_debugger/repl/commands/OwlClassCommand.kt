@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.optimization.extractSyntacticLocalityModule
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxParserImpl
import org.semanticweb.owlapi.manchestersyntax.renderer.ParserException
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException
import java.io.PrintStream
import kotlin.streams.asSequence

class OwlClassCommand : REPLCommand(name = "owlclass"), KoinComponent {
    private val logger: Logger by inject()

    class SyntacticExtractionOptions : OptionGroup() {
        val classRelationDepth by option().int().default(-1)
    }

    val moduleExtraction by option().groupChoice(
        "syntactic" to SyntacticExtractionOptions()
    )

    val rawClassExpression: String by argument()

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
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
            manchesterParser.parseClassExpression(rawClassExpression)
        } catch (e: ParserException) {
            val printStream = PrintStream(logger.logStream())
            e.printStackTrace(printStream)
            printStream.flush()
            logger.error("Could not parse Manchester class expression.")
            throw ProgramResult(-1)
        }

        val ontology = when (val it = moduleExtraction) {
            is SyntacticExtractionOptions -> {
                logger.debug("Axioms before module extraction: ${knowledgeBase.ontology.axiomCount}.")
                val module = extractSyntacticLocalityModule(
                    knowledgeBase,
                    classExpression.signature().asSequence().toSet(),
                    it.classRelationDepth
                )
                logger.debug("Axioms after module extraction: ${module.axiomCount}.")
                module
            }
            else -> knowledgeBase.ontology
        }

        val reasoner = knowledgeBase.getOwlClassExpressionReasoner(ontology)

        val instances = try {
            reasoner.getInstances(classExpression)
        } catch (e: InconsistentOntologyException) {
            val printStream = PrintStream(logger.logStream())
            e.printStackTrace(printStream)
            printStream.flush()
            logger.error("The ontology is not consistent.")

            throw ProgramResult(-1)
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
    }
}