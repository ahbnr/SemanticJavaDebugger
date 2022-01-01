package de.ahbnr.semanticweb.java_debugger.repl.commands.utils

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.optimization.extractSyntacticLocalityModule
import de.ahbnr.semanticweb.java_debugger.repl.KnowledgeBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxParserImpl
import org.semanticweb.owlapi.manchestersyntax.renderer.ParserException
import org.semanticweb.owlapi.model.OWLNamedIndividual
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException
import org.semanticweb.owlapi.reasoner.NodeSet
import java.io.PrintStream
import kotlin.streams.asSequence

class OwlClassExpressionEvaluator(
    val knowledgeBase: KnowledgeBase,
    val quiet: Boolean
) : KoinComponent {
    private val logger: Logger by inject()

    var doSyntacticExtraction: Boolean = false
    var classRelationDepth: Int = -1

    fun evaluate(expression: String): NodeSet<OWLNamedIndividual>? {
        val manchesterParser = ManchesterOWLSyntaxParserImpl(
            knowledgeBase.ontology.owlOntologyManager.ontologyConfigurator,
            knowledgeBase.ontology.owlOntologyManager.owlDataFactory
        )
        for ((prefixName, prefixUri) in knowledgeBase.prefixNameToUri) {
            manchesterParser.prefixManager.setPrefix(prefixName, prefixUri)
        }
        manchesterParser.setDefaultOntology(knowledgeBase.ontology)

        val classExpression = try {
            manchesterParser.parseClassExpression(expression)
        } catch (e: ParserException) {
            val message = e.message
            if (message != null) {
                logger.log(message)
                logger.log("")
            }
            logger.error("Could not parse Manchester class expression.")
            return null
        }

        val ontology = if (doSyntacticExtraction) {
            if (!quiet)
                logger.debug("Axioms before module extraction: ${knowledgeBase.ontology.axiomCount}.")

            val module = extractSyntacticLocalityModule(
                knowledgeBase,
                classExpression.signature().asSequence().toSet(),
                classRelationDepth
            )

            if (!quiet)
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

            return null
        }

        return instances
    }
}