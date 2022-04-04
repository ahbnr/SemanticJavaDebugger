package de.ahbnr.semanticweb.sjdb.repl.commands.utils

import de.ahbnr.semanticweb.sjdb.logging.Logger
import de.ahbnr.semanticweb.sjdb.repl.CloseableOWLReasoner
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import openllet.core.OpenlletOptions
import openllet.owlapi.OpenlletReasoner
import openllet.owlapi.explanation.PelletExplanation
import openllet.owlapi.explanation.io.manchester.ManchesterSyntaxExplanationRenderer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.PrintWriter

class ConsistencyChecker(
    val knowledgeBase: KnowledgeBase,
) {
    sealed class Result : AutoCloseable {
        class Inconsistent(
            private val reasoner: CloseableOWLReasoner
        ) : Result(), KoinComponent {
            private val logger: Logger by inject()

            fun explain() {
                if (reasoner !is OpenlletReasoner) {
                    logger.debug("(Explanations are available when using Openllet reasoner.)")
                    return
                }

                val renderer = ManchesterSyntaxExplanationRenderer()
                val out = PrintWriter(logger.logStream())
                renderer.startRendering(out)

                val explainer = PelletExplanation(reasoner)
                logger.emphasize("Why is the knowledge base inconsistent?")
                logger.emphasize("")
                renderer.render(explainer.inconsistencyExplanations)

                renderer.endRendering()
            }

            override fun close() {
                reasoner.close()
            }
        }

        object Consistent : Result() {
            override fun close() {}
        }
    }

    fun check(): Result {
        // Needed to enable explanations for Openllet, see internals of PelletExplanation.setup() method.
        // We manipulate it directly so that we can deactivate it later.
        val originalTracingSetting = OpenlletOptions.USE_TRACING
        OpenlletOptions.USE_TRACING = true

        return try {
            knowledgeBase
                .getConsistencyReasoner()
                .use { reasoner ->
                    val isConsistent = reasoner.isConsistent

                    if (isConsistent)
                        Result.Consistent
                    else
                        Result.Inconsistent(reasoner)
                }
        } finally {
            OpenlletOptions.USE_TRACING = originalTracingSetting
        }
    }
}