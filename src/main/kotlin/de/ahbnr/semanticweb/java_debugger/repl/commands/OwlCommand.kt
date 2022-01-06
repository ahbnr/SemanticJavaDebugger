@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.OwlExpressionEvaluator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.streams.asSequence

class OwlCommand : REPLCommand(name = "owl"), KoinComponent {
    private val logger: Logger by inject()

    class SyntacticExtractionOptions : OptionGroup() {
        val classRelationDepth by option().int().default(-1)
    }

    val moduleExtraction by option().groupChoice(
        "syntactic" to SyntacticExtractionOptions()
    )

    private val instancesOfMode = "instancesOf"
    private val isSatisfiableMode = "isSatisfiable"
    val mode by argument().choice(instancesOfMode, isSatisfiableMode)

    val rawClassExpression: String by argument()

    override fun run() {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        val evaluator = OwlExpressionEvaluator(knowledgeBase, quiet = false)
        when (val it = moduleExtraction) {
            is SyntacticExtractionOptions -> {
                evaluator.doSyntacticExtraction = true
                evaluator.classRelationDepth = it.classRelationDepth
            }
        }

        when (mode) {
            instancesOfMode -> {
                val instances = evaluator.getInstances(rawClassExpression) ?: throw ProgramResult(-1)

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
            isSatisfiableMode -> {
                val isSatisfiable = evaluator.isSatisfiable(rawClassExpression) ?: throw ProgramResult(-1)
                if (isSatisfiable) {
                    logger.success("true")
                } else {
                    logger.error("false")
                }
            }
        }
    }
}