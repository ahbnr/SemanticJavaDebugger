package de.ahbnr.semanticweb.java_debugger.repl

import com.github.ajalt.clikt.core.ProgramResult
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.model.OWLOntologyChangeListener
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class SemanticDebuggerState(
    val compilerTmpDir: Path = Paths.get("") // CWD
) : KoinComponent {
    private val logger: Logger by inject()

    var applicationDomainDefFile: String? = null
    var sourcePath: Path? = null
    var knowledgeBase: KnowledgeBase? = null
        set(value) {
            removeOntologyListener()
            field = value
            if (logOntologyChanges) setOntologyListener()
        }
    var targetReasoner: ReasonerId = ReasonerId.PureJenaReasoner.JenaOwlMicro

    @OptIn(ExperimentalTime::class)
    var lastCommandDuration: Duration? = null

    private var ontologyListener: OWLOntologyChangeListener? = null
    private fun removeOntologyListener() {
        val ontology = knowledgeBase?.ontology
        val currentOntologyListener = ontologyListener
        ontologyListener = null

        if (ontology != null && currentOntologyListener != null) {
            ontology.owlOntologyManager.removeOntologyChangeListener(currentOntologyListener)
        }
    }

    private fun setOntologyListener() {
        removeOntologyListener()

        val ontology = knowledgeBase?.ontology
        if (ontology != null) {
            val newChangeListener = OWLOntologyChangeListener { changes ->
                for (change in changes) {
                    logger.debug("Change: $change")
                }
            }

            ontology.owlOntologyManager.addOntologyChangeListener(newChangeListener)
            ontologyListener = newChangeListener
        }
    }

    var logOntologyChanges: Boolean = true
        set(value) {
            if (value != field) {
                field = value

                if (value) setOntologyListener()
                else removeOntologyListener()
            }
        }

    var logReasoner: Boolean = false

    fun tryGetKnowledgeBase(): KnowledgeBase {
        val knowledgeBase = this.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        return knowledgeBase
    }

}