package de.ahbnr.semanticweb.java_debugger.repl.commands.infercommands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlin.streams.asSequence

class InstancesOfCommand : ExpressionSubCommand(name = "instancesOf") {
    private val representativeOnly: Boolean by option().flag(default = false)

    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()
        val evaluator = getEvaluator()

        val instances =
            (
                    if (representativeOnly)
                        evaluator.getRepresentativeInstances(rawDlExpression)
                    else
                        evaluator.getInstances(rawDlExpression))
                ?.asSequence() ?: throw ProgramResult(-1)

        var foundInstances = false
        for ((individualIdx, individual) in instances.withIndex()) {
            foundInstances = true

            val prefixUriAndName =
                knowledgeBase.uriToPrefixName.entries.find { (uri, _) ->
                    individual.iri.startsWith(uri)
                }

            val prefixedIri = if (prefixUriAndName != null) {
                val (prefixUri, prefixName) = prefixUriAndName

                individual.iri.replaceRange(prefixUri.indices, "$prefixName:").toString()
            } else individual.iri.toString()


            logger.log(prefixedIri, appendNewline = false)

            val rdfGraph = knowledgeBase.ontology.asGraphModel()
            val rdfResource = rdfGraph.getResource(individual.iri.toString())
            if (rdfGraph.containsResource(rdfResource)) {
                val varName = "?i$individualIdx"
                knowledgeBase.setVariable(varName, rdfResource)

                logger.debug(" as $varName")
            } else
                logger.warning(" (no RDF node found)")
        }

        if (!foundInstances) {
            logger.log("Found no instances for this class.")
        }
    }
}