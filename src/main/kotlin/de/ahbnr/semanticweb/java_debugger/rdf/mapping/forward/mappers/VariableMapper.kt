@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.*
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.IMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.TripleCollector
import org.apache.jena.graph.Triple
import org.apache.jena.graph.impl.GraphBase
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.util.iterator.ExtendedIterator

class VariableMapper(
    private val ns: Namespaces
): IMapper {
    private class Graph(
        private val jvmState: JvmState,
        private val ns: Namespaces
    ): GraphBase() {
        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)

            fun addVariable(frameDepth: Int, variable: LocalVariable) {
                val subject = genLocalVariableURI(frameDepth, variable, ns)
                tripleCollector.addStatement(
                    subject,
                    ns.rdf + "type",
                    ns.java + "Variable"
                )
            }

            fun addVariables() {
                // FIXME: Handle more than current stack frame
                // FIXME: Handle multiple threads?
                val frame = jvmState.pausedThread.frame(0)
                val variables = frame.visibleVariables()

                if (variables != null) {
                    val values = frame.getValues(variables)

                    for ((variable, value) in values) {
                        addVariable(0, variable)
                        val variableURI = genLocalVariableURI(0, variable, ns)

                        when (value) {
                            is ObjectReference -> {
                                when (val referenceType = value.referenceType()) {
                                    is ClassType -> {
                                        val objectURI = ObjectMapper.genObjectURI(value, ns)

                                        tripleCollector.addStatement(
                                            variableURI,
                                            ns.java + "storesReferenceTo",
                                            objectURI
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            addVariables()

            return tripleCollector.buildIterator()
        }
    }

    override fun extendModel(jvmState: JvmState, outputModel: Model) {
        val graph = Graph(jvmState, ns)
        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }

    companion object {
        fun genLocalVariableURI(stackFrameDepth: Int, variable: LocalVariable, ns: Namespaces): String =
            "${ns.run}localvariable_frame${stackFrameDepth}_${variable.name()}"
    }
}