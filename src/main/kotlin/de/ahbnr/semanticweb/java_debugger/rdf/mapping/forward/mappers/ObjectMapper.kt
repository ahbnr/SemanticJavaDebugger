@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.debugging.JvmObjectIterator
import de.ahbnr.semanticweb.java_debugger.debugging.ReferenceContexts
import de.ahbnr.semanticweb.java_debugger.debugging.mirrors.IterableMirror
import de.ahbnr.semanticweb.java_debugger.debugging.mirrors.utils.MirroringError
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.BuildParameters
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.IMapper
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
        private val buildParameters: BuildParameters
    ) : GraphBase(), KoinComponent {
        private val URIs: OntURIs by inject()
        private val logger: Logger by inject()

        val referenceContexts = ReferenceContexts()
        private val valueMapper = ValueToNodeMapper()

        private val iterator = JvmObjectIterator(
            buildParameters.jvmState.pausedThread,
            buildParameters.limiter,
            referenceContexts
        )
        private val allObjects = iterator.iterateObjects().toList()

        // Names of those component types of arrays and iterables for which typed sequence element triples
        // already have been added
        private val mappedSequenceComponentTypes = mutableSetOf<String>()

        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)

            fun addReferenceOrNullClass(referenceTypeURI: String) =
                de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.addReferenceOrNullClass(
                    referenceTypeURI,
                    tripleCollector,
                    URIs
                )

            fun addField(field: Field, value: Value?, parentURI: String) {
                if (buildParameters.limiter.canFieldBeSkipped(field))
                    return

                val fieldPropertyName = URIs.prog.genFieldURI(field)
                // we model a field as an instance of the field property of the class.
                // That one is created by the ClassMapper

                // let's find out the object name, i.e. the name of the field value in case of a reference type value,
                // or the value itself, in case of a primitive value
                val valueNode = valueMapper.map(value)

                if (valueNode != null) {
                    tripleCollector.addStatement(
                        parentURI,
                        fieldPropertyName,
                        valueNode
                    )
                }
            }

            fun addFields(objectSubject: String, objectReference: ObjectReference, classType: ClassType) {
                val fieldValues =
                    objectReference.getValues(classType.allFields()) // allFields does capture the fields of superclasses

                for ((field, value) in fieldValues) {
                    if (field.isStatic) // Static fields are handled by addStaticClassMembers
                        continue

                    addField(field, value, objectSubject)
                }
            }

            /**
             * This is actually more part of the static structure.
             * So it really should belong into ClassMapper.
             *
             * However, two arguments to put it here:
             *
             * * to avoid unnecessary triples, we should only generate them if there actually
             *   is a non-empty sequence being mapped
             * * for Iterables, due to type-erasure, we can only generate them if we can extract the
             *   concrete component type from the first element of a mapped, non-empty sequence
             *
             * But this also means, we dont get this static information if there are only empty arrays
             * // TODO Evaluate the effects of this decision
             */
            fun addSequenceTypingTriples(componentType: Type) {
                val componentTypeName = componentType.name()

                if (mappedSequenceComponentTypes.contains(componentTypeName)) {
                    return
                }
                mappedSequenceComponentTypes.add(componentTypeName)

                val typedSequenceElementURI = URIs.prog.genTypedSequenceElementURI(componentTypeName)

                // hasElement<type> Relation
                val typedHasElementURI = URIs.prog.genTypedHasElementURI(componentTypeName)

                tripleCollector.addStatement(
                    typedHasElementURI,
                    URIs.rdf.type,
                    URIs.owl.ObjectProperty
                )

                tripleCollector.addStatement(
                    typedHasElementURI,
                    URIs.rdf.type,
                    URIs.owl.InverseFunctionalProperty
                )

                tripleCollector.addStatement(
                    typedHasElementURI,
                    URIs.rdfs.subPropertyOf,
                    URIs.java.hasElement
                )

                // Removed this part:
                //   We would have to declare the domain as the union of the array type and all possible
                //   iterable implementors.
                //   This is complex and does not have much use, so we dont declare the domain
                // val containerTypeUri = URIs.prog.genReferenceTypeURI(containerType)
                // tripleCollector.addStatement(
                //     typedHasElementURI,
                //     URIs.rdfs.domain,
                //     containerTypeUri
                // )

                tripleCollector.addStatement(
                    typedHasElementURI,
                    URIs.rdfs.range,
                    typedSequenceElementURI
                )

                tripleCollector.addStatement(
                    typedSequenceElementURI,
                    URIs.rdf.type,
                    URIs.owl.Class
                )

                when (componentType) {
                    is PrimitiveType -> {
                        tripleCollector.addStatement(
                            typedSequenceElementURI,
                            URIs.rdfs.subClassOf,
                            URIs.java.PrimitiveSequenceElement
                        )

                        // storesPrimitive Relation
                        val typedStoresPrimitiveURI =
                            URIs.prog.genTypedStoresPrimitiveURI(componentTypeName)

                        tripleCollector.addStatement(
                            typedStoresPrimitiveURI,
                            URIs.rdf.type,
                            URIs.owl.DatatypeProperty
                        )

                        tripleCollector.addStatement(
                            typedStoresPrimitiveURI,
                            URIs.rdf.type,
                            URIs.owl.FunctionalProperty
                        )

                        tripleCollector.addStatement(
                            typedStoresPrimitiveURI,
                            URIs.rdfs.subPropertyOf,
                            URIs.java.storesPrimitive
                        )

                        tripleCollector.addStatement(
                            typedStoresPrimitiveURI,
                            URIs.rdfs.domain,
                            typedSequenceElementURI
                        )

                        val datatypeURI = URIs.java.genPrimitiveTypeURI(componentType)
                        if (datatypeURI == null) {
                            logger.error("Unknown primitive data type: $componentType.")
                            return
                        }
                        tripleCollector.addStatement(
                            typedStoresPrimitiveURI,
                            URIs.rdfs.range,
                            datatypeURI
                        )

                        tripleCollector.addStatement(
                            typedSequenceElementURI,
                            URIs.rdfs.subClassOf,
                            tripleCollector.addCollection(
                                TripleCollector.CollectionObject.OWLSome(
                                    typedStoresPrimitiveURI, NodeFactory.createURI(datatypeURI)
                                )
                            )
                        )
                    }
                    is ReferenceType -> {
                        tripleCollector.addStatement(
                            typedSequenceElementURI,
                            URIs.rdfs.subClassOf,
                            URIs.java.`SequenceElement%3CObject%3E`
                        )

                        // storesReference Relation
                        val typedStoresReferenceURI =
                            URIs.prog.genTypedStoresReferenceURI(componentTypeName)

                        tripleCollector.addStatement(
                            typedStoresReferenceURI,
                            URIs.rdf.type,
                            URIs.owl.ObjectProperty
                        )

                        tripleCollector.addStatement(
                            typedStoresReferenceURI,
                            URIs.rdf.type,
                            URIs.owl.FunctionalProperty
                        )

                        tripleCollector.addStatement(
                            typedStoresReferenceURI,
                            URIs.rdfs.subPropertyOf,
                            URIs.java.storesReference
                        )

                        tripleCollector.addStatement(
                            typedStoresReferenceURI,
                            URIs.rdfs.domain,
                            typedSequenceElementURI
                        )

                        val referenceURI = URIs.prog.genReferenceTypeURI(componentType)
                        tripleCollector.addStatement(
                            typedStoresReferenceURI,
                            URIs.rdfs.range,
                            addReferenceOrNullClass(referenceURI)
                        )

                        tripleCollector.addStatement(
                            typedSequenceElementURI,
                            URIs.rdfs.subClassOf,
                            tripleCollector.addCollection(
                                TripleCollector.CollectionObject.OWLSome(
                                    typedStoresReferenceURI, addReferenceOrNullClass(referenceURI)
                                )
                            )
                        )
                    }
                    else -> {
                        logger.error("Encountered unknown array component type.")
                        return
                    }
                }
            }

            fun addSequence(
                containerURI: String,
                containerRef: ObjectReference,
                cardinality: Int,
                componentType: Type?,
                components: List<Value?>
            ) {
                // Encode container size in cardinality restriction
                // (otherwise we won't be able to reliably query for arrays / iterables by their size due to the open world assumption)
                tripleCollector.addStatement(
                    containerURI,
                    URIs.rdf.type,
                    tripleCollector.addCollection(
                        TripleCollector.CollectionObject.OWLCardinalityRestriction(
                            onPropertyUri = URIs.java.hasElement,
                            onClassUri = URIs.java.SequenceElement,
                            cardinality = TripleCollector.CollectionObject.CardinalityType.Exactly(cardinality)
                        )
                    )
                )

                // Component type might be unloaded
                if (componentType == null) {
                    return
                }

                addSequenceTypingTriples(componentType)

                // # More concrete hasElement relation
                // Create sub-relation of hasElement<Type> relation for this particular array/iterable object to encode
                // the container size in the cardinality
                val typedHasElementURI = URIs.prog.genTypedHasElementURI(componentType.name())
                val typedSequenceElementURI = URIs.prog.genTypedSequenceElementURI(componentType.name())

                // add the actual elements
                for ((idx, elementValue) in components.withIndex()) {
                    val elementInstanceURI = URIs.run.genSequenceElementInstanceURI(containerRef, idx)
                    tripleCollector.addStatement(
                        elementInstanceURI,
                        URIs.rdf.type,
                        URIs.owl.NamedIndividual
                    )

                    tripleCollector.addStatement(
                        elementInstanceURI,
                        URIs.rdf.type,
                        typedSequenceElementURI
                    )

                    tripleCollector.addStatement(
                        elementInstanceURI,
                        URIs.java.hasIndex,
                        NodeFactory.createLiteral(idx.toString(), XSDDatatype.XSDint)
                    )

                    if (idx < components.size - 1) {
                        val nextElementInstanceURI = URIs.run.genSequenceElementInstanceURI(containerRef, idx + 1)

                        tripleCollector.addStatement(
                            elementInstanceURI,
                            URIs.java.hasSuccessor,
                            nextElementInstanceURI
                        )
                    } else {
                        // encode hasSuccessor cardinality, to make it clear that there is no successor
                        // (we are closing the world here!)
                        tripleCollector.addStatement(
                            elementInstanceURI,
                            URIs.rdf.type,
                            tripleCollector.addCollection(
                                TripleCollector.CollectionObject.OWLCardinalityRestriction(
                                    onPropertyUri = URIs.java.hasSuccessor,
                                    onClassUri = URIs.java.SequenceElement,
                                    cardinality = TripleCollector.CollectionObject.CardinalityType.Exactly(0)
                                )
                            )
                        )
                    }

                    tripleCollector.addStatement(
                        containerURI,
                        typedHasElementURI,
                        elementInstanceURI
                    )

                    val valueNode = valueMapper.map(elementValue)
                    if (valueNode != null) {
                        when (componentType) {
                            is PrimitiveType -> {
                                val typedStoresPrimitiveURI = URIs.prog.genTypedStoresPrimitiveURI(componentType.name())
                                tripleCollector.addStatement(
                                    elementInstanceURI,
                                    typedStoresPrimitiveURI,
                                    valueNode
                                )
                            }
                            is ReferenceType -> {
                                val typedStoresReferenceURI = URIs.prog.genTypedStoresReferenceURI(componentType.name())
                                tripleCollector.addStatement(
                                    elementInstanceURI,
                                    typedStoresReferenceURI,
                                    valueNode
                                )
                            }
                            else -> {
                                logger.error("Encountered unknown component type: $componentType")
                                return
                            }
                        }
                    }
                }
            }

            fun addIterableSequence(iterableUri: String, iterableReference: ObjectReference) {
                if (buildParameters.limiter.canSequenceBeSkipped(
                        iterableReference,
                        referenceContexts
                    )
                )
                    return

                try {
                    val iterable = IterableMirror(iterableReference, buildParameters.jvmState.pausedThread)

                    val iterator = iterable.iterator()
                    if (iterator != null) {
                        // FIXME: Potentially infinite iterator! We should add a limiter
                        val elementList = iterator.asSequence().toList()
                        val componentType = elementList.firstOrNull()?.type()

                        addSequence(
                            containerURI = iterableUri,
                            containerRef = iterableReference,
                            cardinality = elementList.size,
                            componentType = componentType,
                            components = elementList
                        )
                    } else {
                        logger.warning("Can not map elements of iterable ${iterableUri} because its iterator() method returns null.")
                    }
                } catch (e: MirroringError) {
                    logger.error(e.message)
                }
            }

            fun addPlainObject(objectURI: String, objectReference: ObjectReference, referenceType: ReferenceType) {
                if (referenceType is ClassType) {
                    addFields(objectURI, objectReference, referenceType)

                    val hasIterableInterface = referenceType.allInterfaces().any { it.name() == "java.lang.Iterable" }
                    if (hasIterableInterface) {
                        addIterableSequence(objectURI, objectReference)
                    }
                } else {
                    logger.error("Encountered regular object which is not of a class type: $objectURI of type $referenceType.")
                }
            }

            fun addArray(objectURI: String, arrayReference: ArrayReference, referenceType: ReferenceType) {
                if (buildParameters.limiter.canSequenceBeSkipped(
                        arrayReference,
                        referenceContexts
                    )
                ) {
                    return
                }

                if (referenceType !is ArrayType) {
                    logger.error("Encountered array whose type is not an array type: Object $objectURI of type $referenceType.")
                    return
                }

                val arrayLength = arrayReference.length()

                val componentType = try {
                    referenceType.componentType()
                } catch (e: ClassNotLoadedException) {
                    if (arrayLength > 0) {
                        logger.error("Array of unloaded component type that has elements. That should not happen.")
                    }

                    null
                }

                addSequence(
                    containerURI = objectURI,
                    containerRef = arrayReference,
                    cardinality = arrayLength,
                    componentType,
                    arrayReference.values
                )
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

                val referenceType = objectReference.referenceType()

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

                // it is of a particular java class
                tripleCollector.addStatement(
                    objectURI,
                    URIs.rdf.type,
                    URIs.prog.genReferenceTypeURI(referenceType)
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
                    // TODO: Other cases
                }
            }

            fun addStaticClassMembers() {
                // FIXME: Aren't class type instances already handled by the JvmObjectIterator in the allObjects method?
                val classTypes =
                    buildParameters.jvmState.pausedThread.virtualMachine().allClasses().filterIsInstance<ClassType>()

                for (classType in classTypes) {
                    if (!classType.isPrepared)
                        continue // skip those class types which have not been fully prepared in the vm state yet

                    val fieldValues = classType.getValues(classType.fields().filter { it.isStatic })

                    for ((field, value) in fieldValues) {
                        addField(field, value, URIs.prog.genReferenceTypeURI(classType))
                    }
                }
            }

            fun addObjects() {
                for (obj in allObjects) {
                    addObject(obj)
                }
            }

            addObjects()
            addStaticClassMembers()

            return tripleCollector.buildIterator()
        }
    }

    override fun extendModel(buildParameters: BuildParameters, outputModel: Model) {
        val graph = Graph(buildParameters)
        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }
}