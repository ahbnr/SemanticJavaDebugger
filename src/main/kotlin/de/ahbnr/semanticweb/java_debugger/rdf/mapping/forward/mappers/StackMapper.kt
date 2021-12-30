@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers

import com.sun.jdi.AbsentInformationException
import com.sun.jdi.StackFrame
import com.sun.jdi.Value
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.BuildParameters
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.IMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.LocalVariableInfo
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.MethodInfo
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
        private val buildParameters: BuildParameters
    ) : GraphBase(), KoinComponent {
        private val URIs: OntURIs by inject()
        private val logger: Logger by inject()

        private val valueMapper = ValueToNodeMapper()

        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)

            fun addLocalVariable(
                stackFrameURI: String,
                frame: StackFrame,
                variable: LocalVariableInfo,
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
                        URIs.prog.genVariableDeclarationURI(variable),
                        valueObject
                    )
                }
            }

            fun addLocalVariables(frameDepth: Int, frameSubject: String, frame: StackFrame) {
                val jdiMethod = frame.location().method()
                val methodInfo = MethodInfo(jdiMethod, buildParameters)
                val methodVariableDeclarations = methodInfo.variables

                val variables = try {
                    frame.visibleVariables()
                } catch (e: AbsentInformationException) {
                    logger.debug("Can not load variable information for frame $frameDepth")
                    null
                }

                if (variables != null) {
                    val values = frame.getValues(variables)

                    for ((variable, value) in values) {
                        val variableInfo = methodVariableDeclarations.find { it.jdiLocalVariable == variable }

                        if (variableInfo == null) {
                            logger.error("Could not retrieve information on a variable declaration for a stack variable.")
                            continue
                        }

                        addLocalVariable(
                            frameSubject,
                            frame,
                            variableInfo,
                            value
                        )
                    }
                }
            }

            fun addStackFrame(frameDepth: Int, frame: StackFrame) {
                val frameSubject = URIs.run.genFrameURI(frameDepth)

                tripleCollector.addStatement(
                    frameSubject,
                    URIs.rdf.type,
                    URIs.owl.NamedIndividual
                )

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

                // ...and it oftentimes has a `this` reference:
                val thisRef = frame.thisObject()
                if (thisRef != null) {
                    val thisObjectNode = valueMapper.map(thisRef)

                    if (thisObjectNode != null) {
                        tripleCollector.addStatement(
                            frameSubject,
                            URIs.java.`this`,
                            thisObjectNode
                        )
                    } else {
                        logger.error("Could not find `this` object for frame. This should never happen.")
                    }
                }

                // ...and it holds some variables
                addLocalVariables(frameDepth, frameSubject, frame)
            }

            fun addStackFrames() {
                // TODO: Handle multiple threads
                val numFrames = buildParameters.jvmState.pausedThread.frameCount()
                for (i in 0 until numFrames) {
                    addStackFrame(i, buildParameters.jvmState.pausedThread.frame(i))
                }
            }

            addStackFrames()

            return tripleCollector.buildIterator()
        }
    }

    override fun extendModel(buildParameters: BuildParameters, outputModel: Model) {
        val graph = Graph(buildParameters)
        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }
}