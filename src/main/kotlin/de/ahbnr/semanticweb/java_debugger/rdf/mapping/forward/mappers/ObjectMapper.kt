@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.IMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.JavaType
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

class ObjectMapper : IMapper {
    private class Graph(
        private val jvmState: JvmState,
        private val limiter: MappingLimiter
    ) : GraphBase(), KoinComponent {
        private val URIs: OntURIs by inject()
        private val logger: Logger by inject()

        private val valueMapper = ValueToNodeMapper()

        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)

            fun addField(field: Field, value: Value?, objectSubject: String, classType: ClassType) {
                if (field.isStatic) {
                    return // FIXME: handle static fields
                }

                val fieldPropertyName = URIs.prog.genFieldURI(field)
                // we model a field as an instance of the field property of the class.
                // That one is created by the ClassMapper

                // let's find out the object name, i.e. the name of the field value in case of a reference type value,
                // or the value itself, in case of a primitive value
                val valueNode = valueMapper.map(value)

                if (valueNode != null) {
                    tripleCollector.addStatement(
                        objectSubject,
                        fieldPropertyName,
                        valueNode
                    )
                }
            }

            fun addFields(objectSubject: String, objectReference: ObjectReference, classType: ClassType) {
                val fieldValues =
                    objectReference.getValues(classType.allFields()) // allFields does capture the fields of superclasses

                for ((field, value) in fieldValues) {
                    if (!field.isPublic && limiter.isShallow(classType)) {
                        continue
                    }

                    addField(field, value, objectSubject, classType)
                }
            }

            fun addPlainObject(objectURI: String, objectReference: ObjectReference, referenceType: ReferenceType) {
                if (referenceType is ClassType) {
                    addFields(objectURI, objectReference, referenceType)
                } else {
                    logger.error("Encountered regular object which is not of a class type: $objectURI of type $referenceType.")
                }
            }

            fun addArray(objectURI: String, arrayReference: ArrayReference, referenceType: ReferenceType) {
                if (!limiter.isDeep(referenceType) && !jvmState.isReferencedByVariable(arrayReference) && !arrayReference.referringObjects(
                        Long.MAX_VALUE
                    )
                        .any { limiter.isDeep(it.referenceType()) } // FIXME: Of course, this is incredibly slow
                ) {
                    return
                }

                if (referenceType !is ArrayType) {
                    logger.error("Encountered array whose type is not an array type: Object $objectURI of type $referenceType.")
                    return
                }

                val componentType = try {
                    JavaType.LoadedType(referenceType.componentType())
                } catch (e: ClassNotLoadedException) {
                    JavaType.UnloadedType(referenceType.componentTypeName())
                }

                when (componentType) {
                    is JavaType.LoadedType -> when (componentType.type) {
                        is PrimitiveType -> {
                            // # More concrete hasElement relation

                            // Create sub-relation of hasElement<Type> relation for this particular array object to encode
                            // the array size in the cardinality
                            val typedHasElementURI = URIs.prog.genTypedHasElementURI(componentType.type)
                            val sizedHasElementURI = URIs.run.genSizedHasElementURI(arrayReference)
                            tripleCollector.addStatement(
                                sizedHasElementURI,
                                URIs.rdfs.subPropertyOf,
                                typedHasElementURI
                            )

                            tripleCollector.addStatement(
                                sizedHasElementURI,
                                URIs.rdfs.domain,
                                tripleCollector.addCollection(
                                    TripleCollector.CollectionObject.OWLOneOf.fromURIs(
                                        listOf(
                                            objectURI
                                        )
                                    )
                                )
                            )

                            val typedArrayElementURI = URIs.prog.genTypedArrayElementURI(componentType.type)
                            tripleCollector.addStatement(
                                sizedHasElementURI,
                                URIs.rdfs.range,
                                typedArrayElementURI
                            )

                            tripleCollector.addStatement(
                                sizedHasElementURI,
                                URIs.owl.cardinality,
                                NodeFactory.createLiteral(
                                    arrayReference.length().toString(),
                                    XSDDatatype.XSDnonNegativeInteger
                                )
                            )

                            // add the actual elements
                            for (i in 0 until arrayReference.length()) {
                                val value = arrayReference.getValue(i)

                                val arrayElementInstanceURI = URIs.run.genArrayElementInstanceURI(arrayReference, i)
                                tripleCollector.addStatement(
                                    arrayElementInstanceURI,
                                    URIs.rdf.type,
                                    URIs.owl.NamedIndividual
                                )

                                tripleCollector.addStatement(
                                    arrayElementInstanceURI,
                                    URIs.rdf.type,
                                    typedArrayElementURI
                                )

                                tripleCollector.addStatement(
                                    arrayElementInstanceURI,
                                    URIs.java.hasIndex,
                                    NodeFactory.createLiteral(i.toString(), XSDDatatype.XSDint)
                                )

                                val valueNode = valueMapper.map(value)

                                if (valueNode != null) {
                                    val typedStoresPrimitiveURI = URIs.prog.genTypedStoresPrimitiveURI(referenceType)
                                    tripleCollector.addStatement(
                                        arrayElementInstanceURI,
                                        typedStoresPrimitiveURI,
                                        valueNode
                                    )
                                }

                                tripleCollector.addStatement(
                                    objectURI,
                                    sizedHasElementURI,
                                    arrayElementInstanceURI
                                )
                            }
                        }
                        is ReferenceType -> Unit // FIXME: Deal with this case
                    }
                    is JavaType.UnloadedType -> Unit // FIXME: Deal with this case
                }
            }

            fun addString(objectURI: String, stringReference: StringReference, referenceType: ReferenceType) {
                addPlainObject(objectURI, stringReference, referenceType)

                tripleCollector.addStatement(
                    objectURI,
                    URIs.java.hasStringValue,
                    NodeFactory.createLiteral(stringReference.value(), XSDDatatype.XSDstring)
                )
            }

            fun addObject(objectReference: ObjectReference) {
                val objectURI = URIs.run.genObjectURI(objectReference)

                // The object is a particular individual (not a class/concept)
                tripleCollector.addStatement(
                    objectURI,
                    URIs.rdf.type,
                    URIs.owl.NamedIndividual
                )

                // it is a java object
                tripleCollector.addStatement(
                    objectURI,
                    URIs.rdf.type,
                    URIs.java.Object
                )

                // as such, it has been assigned a unique ID by the VM JDWP agent:
                tripleCollector.addStatement(
                    objectURI,
                    URIs.java.hasJDWPObjectId,
                    NodeFactory.createLiteral(
                        objectReference.uniqueID().toString(),
                        XSDDatatype.XSDlong
                    )
                )

                val referenceType = objectReference.referenceType()
                // it is of a particular java class
                tripleCollector.addStatement(
                    objectURI,
                    URIs.rdf.type,
                    URIs.prog.genReferenceTypeURI(referenceType) // FIXME: we model Java classes as owl classes here, instead of being individuals. Not sure what the right design is here
                )

                when (objectReference) {
                    is ArrayReference -> addArray(objectURI, objectReference, referenceType)
                    is ClassLoaderReference -> Unit
                    is ClassObjectReference -> Unit
                    is ModuleReference -> Unit
                    is StringReference -> addString(objectURI, objectReference, referenceType)
                    is ThreadGroupReference -> Unit
                    is ThreadReference -> Unit
                    // "normal object"
                    else -> addPlainObject(objectURI, objectReference, referenceType)
                    //FIXME: Other cases
                }
            }

            fun addObjects() {
                for (obj in jvmState.allObjects(limiter)) {
                    addObject(obj)
                }
            }

            addObjects()

            return tripleCollector.buildIterator()
        }
    }

    override fun extendModel(jvmState: JvmState, outputModel: Model, limiter: MappingLimiter) {
        val graph = Graph(jvmState, limiter)
        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }
}