@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers

import com.sun.jdi.ClassType
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.IMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.TripleCollector
import org.apache.jena.datatypes.TypeMapper
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.graph.impl.GraphBase
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.util.iterator.ExtendedIterator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ObjectMapper : IMapper {
    private class Graph(
        private val jvmState: JvmState
    ) : GraphBase(), KoinComponent {
        private val URIs: OntURIs by inject()

        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)
            val tm = TypeMapper.getInstance()

            fun addField(field: Field, value: Value?, objectSubject: String, classType: ClassType) {
                // we model a field as an instance of the field property of the class.
                // That one is created by the ClassMapper

                // let's find out the object name, i.e. the name of the field value in case of a reference type value,
                // or the value itself, in case of a primitive value
                val valueObject = when (value) {
                    null -> URIs.java.`null`
                    is ObjectReference -> URIs.run.genObjectURI(value)
                    else -> null // FIXME: Handle other cases
                }

                if (valueObject != null) {
                    val fieldPropertyName = URIs.prog.genFieldURI(classType, field)
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
                    addField(field, value, objectSubject, classType)
                }
            }

            fun addObject(objectReference: ObjectReference) {
                when (val referenceType = objectReference.referenceType()) {
                    is ClassType -> {
                        val objectSubject = URIs.run.genObjectURI(objectReference)

                        // The object is a particular individual (not a class/concept)
                        tripleCollector.addStatement(
                            objectSubject,
                            URIs.rdf.type,
                            URIs.owl.NamedIndividual
                        )

                        // it is a java object
                        tripleCollector.addStatement(
                            objectSubject,
                            URIs.rdf.type,
                            URIs.java.Object
                        )

                        // as such, it has been assigned a unique ID by the VM JDWP agent:
                        tripleCollector.addStatement(
                            objectSubject,
                            URIs.java.hasJDWPObjectId,
                            NodeFactory.createLiteral(
                                objectReference.uniqueID().toString(),
                                XSDDatatype.XSDlong
                            )
                        )

                        // it is of a particular java class
                        tripleCollector.addStatement(
                            objectSubject,
                            URIs.rdf.type,
                            URIs.prog.genReferenceTypeURI(referenceType) // FIXME: we model Java classes as owl classes here, instead of being individuals. Not sure what the right design is here
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
        val graph = Graph(jvmState)
        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }
}