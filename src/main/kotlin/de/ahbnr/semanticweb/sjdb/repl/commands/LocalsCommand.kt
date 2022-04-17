@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.sun.jdi.LocalVariable
import com.sun.jdi.ObjectReference
import de.ahbnr.semanticweb.jdi2owl.mapping.OntIRIs
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.LocalVariableInfo
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.rdf.model.ResourceFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LocalsCommand : REPLCommand(name = "locals"), KoinComponent {
    private val URIs: OntIRIs by inject()

    override fun run() {
        val jvmState = tryGetJvmState()

        if (jvmState.pausedThread.frameCount() == 0) {
            logger.error("JVM has not started yet.")
            throw ProgramResult(-1)
        }

        // Careful, no invokeMethod calls should take place from here on to keep this frame reference
        // valid.
        val frame = jvmState.pausedThread.frame(0)

        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase != null) {
            logger.log("Frame URI: ${knowledgeBase.asPrefixNameUri(URIs.run.genFrameIRI(0))}\n")
        }

        val model = knowledgeBase?.ontology?.asGraphModel()
        val hasUniqueId = model?.getProperty(URIs.java.hasUniqueId)

        fun getObjectUri(ref: ObjectReference): String? {
            val objectId = ref.uniqueID()
            if (knowledgeBase != null) {
                val subjects = model!!.listSubjectsWithProperty(
                    hasUniqueId!!,
                    ResourceFactory.createTypedLiteral(objectId.toString(), XSDDatatype.XSDlong)
                )

                if (subjects.hasNext()) {
                    val subject = subjects.nextResource()
                    if (subject.isURIResource) {
                        return knowledgeBase.asPrefixNameUri(subject.uri)
                    }
                }
            }

            return null
        }

        var haveAllUrisBeenDisplayed = true

        val thisObject = frame.thisObject()
        if (thisObject != null) {
            logger.log("this = $thisObject")

            val objectUri = getObjectUri(thisObject)
            if (objectUri != null) {
                logger.log("  Object URI: $objectUri")
            } else {
                if (knowledgeBase?.buildParameters?.limiter?.canReferenceTypeBeSkipped(thisObject.referenceType()) == false) {
                    logger.error("  Could not retrieve URI for `this` object. This should never happen.")
                }

                haveAllUrisBeenDisplayed = false
            }

            logger.log("")
        }

        val variableInfos: Map<LocalVariable, LocalVariableInfo> =
            if (knowledgeBase == null) emptyMap()
            else {
                val method = frame.location().method()
                val declaringTypeInfo = knowledgeBase.buildParameters.typeInfoProvider.getTypeInfo(method.declaringType())
                val methodInfo = declaringTypeInfo.getMethodInfo(method)

                method.variables().associateWith { methodInfo.getVariableInfo(it) }
            }

        val visibleVariables = frame.getValues(frame.visibleVariables())
        for ((variable, value) in visibleVariables) {
            logger.log("${variable.name()} = $value")

            val variableInfo = variableInfos.getOrDefault(variable, null)
            if (knowledgeBase != null && variableInfo != null) {
                val variableUri = knowledgeBase.asPrefixNameUri(URIs.prog.genVariableDeclarationIRI(variableInfo))

                logger.log("  Variable URI: $variableUri")
            }

            if (value is ObjectReference) {
                val objectUri = getObjectUri(value)
                if (objectUri != null) {
                    logger.log("  Object URI: $objectUri")
                } else {
                    haveAllUrisBeenDisplayed = false
                }
            }

            logger.log("")
        }

        if (knowledgeBase == null) {
            logger.debug("Build a knowledge base first, to get URIs of variables and objects.")
        } else if (!haveAllUrisBeenDisplayed) {
            logger.debug("For some objects, no knowledge graph representation is available. Did you build an up-to-date, unlimited knowledge graph?")
        }
    }
}