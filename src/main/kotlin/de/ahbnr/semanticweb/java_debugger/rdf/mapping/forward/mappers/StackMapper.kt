@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.Namespaces
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.IMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.TripleCollector
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.graph.impl.GraphBase
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.util.iterator.ExtendedIterator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class StackMapper : IMapper {
    private class Graph(
        private val jvmState: JvmState,
    ) : GraphBase(), KoinComponent {
        private val URIs: OntURIs by inject()
        private val logger: Logger by inject()

        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)

            fun addLocalVariable(
                frameDepth: Int,
                frameSubject: String,
                frame: StackFrame,
                variable: LocalVariable,
                value: Value
            ) {
                // this *is* a LocalVariable
                val localVariableSubject = URIs.run.genLocalVariableURI(frameDepth, variable)
                tripleCollector.addStatement(
                    localVariableSubject,
                    URIs.rdf.type,
                    URIs.java.LocalVariable
                )

                // ...and the variable has been declared here:
                val method = frame.location().method()
                val classType = method.declaringType()
                tripleCollector.addStatement(
                    localVariableSubject,
                    URIs.java.declaredByVariableDeclaration,
                    URIs.prog.genVariableDeclarationURI(variable, method, classType)
                )

                // ...and it carries this value / reference:
                when (value) {
                    is ObjectReference -> {
                        when (value.referenceType()) {
                            is ClassType -> {
                                tripleCollector.addStatement(
                                    localVariableSubject,
                                    URIs.java.storesReferenceTo,
                                    URIs.run.genObjectURI(value)
                                )
                            }
                            //FIXME: handle other cases
                        }
                    }
                }

                // ...and it is part of its frame
                tripleCollector.addStatement(
                    frameSubject,
                    URIs.java.hasLocalVariable,
                    localVariableSubject
                )
            }

            fun addLocalVariables(frameDepth: Int, frameSubject: String, frame: StackFrame) {
                val variables = try {
                    frame.visibleVariables()
                } catch (e: AbsentInformationException) {
                    logger.debug("Can not load variable information for frame $frameDepth")
                    null
                }

                if (variables != null) {
                    val values = frame.getValues(variables)

                    for ((variable, value) in values) {
                        addLocalVariable(frameDepth, frameSubject, frame, variable, value)
                    }
                }
            }

            fun addStackFrame(frameDepth: Int, frame: StackFrame) {
                val frameSubject = URIs.run.genFrameURI(frameDepth)

                // this *is* a stack frame
                tripleCollector.addStatement(
                    frameSubject,
                    URIs.rdf.type,
                    URIs.java.StackFrame
                )

                // ...and it is at a certain depth
                tripleCollector.addStatement(
                    frameSubject,
                    URIs.java.isAtStackDepth,
                    NodeFactory.createLiteral(frameDepth.toString(), XSDDatatype.XSDint)
                )

                // ...and it holds some variables
                addLocalVariables(frameDepth, frameSubject, frame)
            }

            fun addStackFrames() {
                // FIXME: Handle multiple threads?
                val numFrames = jvmState.pausedThread.frameCount()
                for (i in 0 until numFrames) {
                    addStackFrame(i, jvmState.pausedThread.frame(i))
                }
            }

            addStackFrames()

            return tripleCollector.buildIterator()
        }
    }

    override fun extendModel(jvmState: JvmState, outputModel: Model) {
        val graph = Graph(jvmState)
        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }

    companion object {
        fun genLocalVariableURI(stackFrameDepth: Int, variable: LocalVariable, ns: Namespaces): String =
            "${ns.run}localvariable_frame${stackFrameDepth}_${variable.name()}"
    }
}