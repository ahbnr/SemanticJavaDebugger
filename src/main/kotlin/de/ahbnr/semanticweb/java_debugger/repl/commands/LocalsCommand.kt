@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.sun.jdi.ObjectReference
import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.rdf.model.ResourceFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LocalsCommand(
    private val jvmDebugger: JvmDebugger
) : REPLCommand(name = "locals"), KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()


    override fun run() {
        val jvm = jvmDebugger.jvm
        if (jvm == null) {
            logger.error("No JVM is running.")
            throw ProgramResult(-1)
        }

        val jvmState = jvm.state
        if (jvmState == null) {
            logger.error("JVM is currently not paused.")
            throw ProgramResult(-1)
        }

        if (jvmState.pausedThread.frameCount() == 0) {
            logger.error("JVM has not started yet.")
            throw ProgramResult(-1)
        }

        val frame = jvmState.pausedThread.frame(0)

        val knowledgeBase = state.knowledgeBase
        val model = knowledgeBase?.ontology?.asGraphModel()
        val hasJDWPObjectId = model?.getProperty(URIs.java.hasJDWPObjectId)
        var frameHasObjects = false
        var haveAllUrisBeenDisplayed = true

        val visibleVariables = frame.getValues(frame.visibleVariables())
        for ((key, value) in visibleVariables) {
            logger.log(key.name() + " = " + value)

            if (value is ObjectReference) {
                frameHasObjects = true

                val objectId = value.uniqueID()
                if (knowledgeBase != null) {
                    val subjects = model!!.listSubjectsWithProperty(
                        hasJDWPObjectId!!,
                        ResourceFactory.createTypedLiteral(objectId.toString(), XSDDatatype.XSDlong)
                    )

                    if (subjects.hasNext()) {
                        val subject = subjects.nextResource()
                        if (subject.isURIResource) {
                            logger.log("  URI: ${knowledgeBase.asPrefixNameUri(subject.uri)}")
                        }
                    } else {
                        haveAllUrisBeenDisplayed = false
                    }
                }
            }
        }

        if (frameHasObjects && model == null) {
            logger.debug("The current stack frame contains objects. If you build a knowledge graph with `buildkb` first, their URIs will be displayed here.")
        } else if (!haveAllUrisBeenDisplayed) {
            logger.debug("For some objects, no knowledge graph representation is available. Did you build an up-to-date, unlimited knowledge graph?")
        }
    }
}