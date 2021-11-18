@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.*
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.IMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.TripleCollector
import org.apache.jena.datatypes.TypeMapper
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.graph.impl.GraphBase
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.util.iterator.ExtendedIterator

class ObjectMapper(
    private val ns: Namespaces
): IMapper {
    private class Graph(
        private val jvmState: JvmState,
        private val ns: Namespaces
    ): GraphBase() {
        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)
            val tm = TypeMapper.getInstance()

            fun addField(field: Field, value: Value?, objectSubject: String, objectReference: ObjectReference, classType: ClassType) {
                // we model a field as an instance of the field property of the class.
                // That one is created by the ClassMapper

                // lets find out the object name, i.e. the name of the field value in case of a reference type value,
                // or the value itself, in case of a primitive value
                val valueObject = when(value) {
                    null -> ns.java + "null"
                    is ObjectReference -> genObjectURI(value, ns)
                    else -> null // FIXME: Handle other cases
                }

                if (valueObject != null) {
                    val fieldPropertyName = ClassMapper.genFieldPropertyURI(classType, field, ns)
                    tripleCollector.addStatement(
                        objectSubject,
                        fieldPropertyName,
                        valueObject
                    )
                }

                // FIXME: Output a warning in the else case
            }

            fun addFields(objectSubject: String, objectReference: ObjectReference, classType: ClassType) {
                // FIXME: Extend this also to the values from superclasses (allFields())
                val fieldValues = objectReference.getValues(classType.fields())

                for ((field, value) in fieldValues) {
                    addField(field, value, objectSubject, objectReference, classType)
                }
            }

            fun addObject(objectReference: ObjectReference) {
                val referenceType = objectReference.referenceType()

                when (referenceType) {
                    is ClassType -> {
                        val objectSubject = genObjectURI(objectReference, ns)

                        // The object is a particular individual (not a class/concept)
                        tripleCollector.addStatement(
                            objectSubject,
                            ns.rdf + "type",
                            ns.owl + "NamedIndividual"
                        )

                        // it is a java object
                        tripleCollector.addStatement(
                            objectSubject,
                            ns.rdf + "type",
                            ns.java + "Object"
                        )

                        // as such, it has been assigned a unique ID by the VM JDWP agent:
                        tripleCollector.addStatement(
                            NodeFactory.createURI(objectSubject),
                            NodeFactory.createURI(ns.java + "hasJDWPObjectId"),
                            NodeFactory.createLiteral(
                                objectReference.uniqueID().toString(),
                                tm.getSafeTypeByName(ns.java + "long")
                            )
                        )

                        // it is of a particular java class
                        tripleCollector.addStatement(
                            objectSubject,
                            ns.rdf + "type",
                            ClassMapper.genClassTypeURI(
                                referenceType,
                                ns
                            ) // FIXME: we model Java classes as owl classes here, instead of being individuals. Not sure what the right design is here
                        )

                        addFields(objectSubject, objectReference, referenceType)
                    }
                    //FIXME: Other cases
                }
            }

            fun addObjects() {
                for (obj in jvmState.allObjects()) {
                    addObject(obj)
                }
            }

            addObjects()

            return tripleCollector.buildIterator()
        }
    }

    override fun extendModel(jvmState: JvmState, outputModel: Model) {
        val graph = Graph(jvmState, ns)
        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }

    companion object {
        fun genObjectURI(objectReference: ObjectReference, ns: Namespaces): String =
            // FIXME: The documentation is not really clear on this, but experimentally this should be a unique id for the object being referenced (e.g. two references to the same object return the same id here)
            "${ns.run}object_${objectReference.uniqueID()}"
    }
}