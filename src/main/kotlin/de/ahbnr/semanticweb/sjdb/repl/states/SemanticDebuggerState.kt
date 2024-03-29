package de.ahbnr.semanticweb.sjdb.repl.states

import de.ahbnr.semanticweb.jdi2owl.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.MappingSettings
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import de.ahbnr.semanticweb.sjdb.repl.ReasonerId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.model.OWLOntologyChangeListener
import java.nio.file.Path
import java.nio.file.Paths

class SemanticDebuggerState(
    val compilerTmpDir: Path = Paths.get("") // CWD
) : KoinComponent {
    private val logger: Logger by inject()

    val mappingSettings = MappingSettings().apply {
        // use high performance settings as default
        limitSdk = true
        closeReferenceTypes = false
        makeObjectsDistinct = false
    }

    var applicationDomainDefFile: String? = null
    var sourcePath: Path? = null
    var knowledgeBase: KnowledgeBase? = null
        set(value) {
            removeOntologyListener()
            field = value
            if (logOntologyChanges) setOntologyListener()
        }
    var targetReasoner: ReasonerId = ReasonerId.PureJenaReasoner.JenaOwlMicro

    var classPaths: List<Path> = emptyList()

    // Timeout after which any REPL command is forcefully stopped.
    // A timeout of 0 means no timeout will be applied.
    var timeout: Long = 0
    val timeCommandState = TimeCommandState()

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
                    if (change.ontology.ontologyID == ontology.ontologyID) {
                        logger.debug("Change: $change")
                    }
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
}