@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers

import com.sun.jdi.AbsentInformationException
import com.sun.jdi.LocalVariable
import com.sun.jdi.StackFrame
import com.sun.jdi.Value
import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.IMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.TripleCollector
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.ValueToNodeMapper
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
        private val limiter: MappingLimiter
    ) : GraphBase(), KoinComponent {
        private val URIs: OntURIs by inject()
        private val logger: Logger by inject()

        private val valueMapper = ValueToNodeMapper()

        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)

            fun addLocalVariable(
                stackFrameURI: String,
                frame: StackFrame,
                variable: LocalVariable,
                value: Value?
            ) {
                val method = frame.location().method()
                val classType = method.declaringType()

                // we model this via the variable declaration property.
                // The property value depends on the kind of value we have here
                val valueObject = valueMapper.map(value)

                if (valueObject != null) {
                    tripleCollector.addStatement(
                        stackFrameURI,
                        URIs.prog.genVariableDeclarationURI(variable, method, classType),
                        valueObject
                    )
                }
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
                        addLocalVariable(frameSubject, frame, variable, value)
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

    override fun extendModel(jvmState: JvmState, outputModel: Model, limiter: MappingLimiter) {
        val graph = Graph(jvmState, limiter)
        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }
}