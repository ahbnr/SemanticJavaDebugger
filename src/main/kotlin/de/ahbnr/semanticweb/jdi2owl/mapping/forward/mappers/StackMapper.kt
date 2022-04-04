@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.jdi2owl.mapping.forward.mappers

import com.sun.jdi.AbsentInformationException
import com.sun.jdi.StackFrame
import com.sun.jdi.Value
import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.OntURIs
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.BuildParameters
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.IMapper
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.LocalVariableInfo
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.MethodInfo
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.TripleCollector
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.ValueToNodeMapper
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
                frameDepth: Int,
                stackFrameURI: String,
                variable: LocalVariableInfo,
                value: Value?
            ) {
                // we model this via the variable declaration property.
                // The property value depends on the kind of value we have here
                val valueObject = valueMapper.map(value)

                if (valueObject != null) {
                    tripleCollector.addStatement(
                        stackFrameURI,
                        URIs.prog.genVariableDeclarationURI(variable),
                        valueObject
                    )

                    // The values of the current stackframe get special, easily accessible names
                    if (frameDepth == 0) {
                        val localVarURI = URIs.local.genLocalVariableURI(variable)
                        tripleCollector.addStatement(
                            localVarURI,
                            URIs.rdf.type,
                            URIs.owl.NamedIndividual
                        )

                        // FIXME: This declares even data values as the same as a local variable.
                        //   Is this valid OWL 2?
                        //   Probably not, 'SameIndividual( local:x "1"^^xsd:int )' fails with a parsing error
                        tripleCollector.addStatement(
                            localVarURI,
                            URIs.owl.sameAs,
                            valueObject
                        )
                    }
                }
            }

            fun addLocalVariables(
                frameDepth: Int,
                frameSubject: String,
                // Careful, no invokeMethod calls should take place from here on to keep this frame reference
                // valid.
                frame: StackFrame
            ) {
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
                            frameDepth,
                            frameSubject,
                            variableInfo,
                            value
                        )
                    }
                }
            }

            fun addStackFrame(
                frameDepth: Int,
                // Careful, no invokeMethod calls should take place from here on to keep this frame reference
                // valid.
                frame: StackFrame
            ) {
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
                    NodeFactory.createLiteralByValue(frameDepth, XSDDatatype.XSDint)
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

                        // For the current frame, we create a quick alias for the this object
                        if (frameDepth == 0) {
                            tripleCollector.addStatement(
                                URIs.local.`this`,
                                URIs.rdf.type,
                                URIs.owl.NamedIndividual
                            )

                            tripleCollector.addStatement(
                                URIs.local.`this`,
                                URIs.owl.sameAs,
                                thisObjectNode
                            )
                        }
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