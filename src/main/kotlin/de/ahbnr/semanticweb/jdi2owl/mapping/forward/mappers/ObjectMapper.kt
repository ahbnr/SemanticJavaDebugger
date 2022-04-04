@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.jdi2owl.mapping.forward.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.jdi2owl.debugging.JvmObjectIterator
import de.ahbnr.semanticweb.jdi2owl.debugging.ReferenceContexts
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.*
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils.MirroringError
import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.OntURIs
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.BuildParameters
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.IMapper
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.TripleCollector
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.ValueToNodeMapper
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.extendsClass
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.implementsInterface
import dk.brics.automaton.Datatypes
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

        init {
            iterator.reportErrors()
        }

        // Names of those component types of arrays and iterables for which typed sequence element triples
        // already have been added
        private val mappedSequenceComponentTypes = mutableSetOf<String>()

        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)

            fun addReferenceOrNullClass(referenceTypeURI: String) =
                de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.addReferenceOrNullClass(
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
                            tripleCollector.addConstruct(
                                TripleCollector.BlankNodeConstruct.OWLSome(
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
                            tripleCollector.addConstruct(
                                TripleCollector.BlankNodeConstruct.OWLSome(
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
                    tripleCollector.addConstruct(
                        TripleCollector.BlankNodeConstruct.OWLObjectCardinalityRestriction(
                            onPropertyUri = URIs.java.hasElement,
                            onClassUri = URIs.java.SequenceElement,
                            cardinality = TripleCollector.BlankNodeConstruct.CardinalityType.Exactly(cardinality)
                        )
                    )
                )

                // The following code closes range set of hasElement for this container object
                //
                // Otherwise, we will not be able to do universal queries a la `hasElement only (storesReference only {null})`
                // Apparently reasoners (at least HermiT and Openllet) can not infer from the cardinality restriction
                //   above, that the elements of the array are restricted to those being explicitly listed.
                //
                // This might be because we have no unique name assumption.
                // I.e. the reasoner can not return a result for `hasElement only {elementX}` given the facts
                //   someArray hasElement elementX
                //   someArray a (hasElement exactly 1 SequenceElement)
                // because there might be some elementY such that
                //   someArray hasElement elementY
                // this does not violate the cardinality restriction, because it could be that
                //   elementY sameAs elementX
                //
                // Still, then `hasElement only {elementX}` can return someArray because
                // it is the same as asking `hasElment only {elementY}`.
                //
                // Maybe this kind of reasoning is just too complex and not implemented?
                //
                // As a workaround, we close the range of hasElement for this particular object explicitly:
                //
                //   inverse hasElement {myContainer} EquivalentTo { ...elements...}
                //
                // In turtle format:
                //   [
                //     a owl:Class ;
                //     owl:oneOf ( ..elements... ) ;
                //     owl:equivalentClass [
                //       a owl:Restriction ;
                //       owl:onProperty [
                //          a owl:ObjectProperty ;
                //          owl:inverseOf java:hasElement
                //       ] ;
                //       owl:someValuesFrom [
                //          a owl:Class ;
                //          owl:oneOf ( myContainer )
                //       ]
                //     ]
                //   ] .
                val componentIris = components
                    .indices
                    .map { idx -> URIs.run.genSequenceElementInstanceURI(containerRef, idx) }
                tripleCollector.addStatement(
                    if (componentIris.isEmpty())
                        NodeFactory.createURI(URIs.owl.Nothing)
                    else
                        tripleCollector.addConstruct(
                            TripleCollector.BlankNodeConstruct.OWLOneOf.fromURIs(componentIris)
                        ),
                    URIs.owl.equivalentClass,
                    tripleCollector.addConstruct(
                        TripleCollector.BlankNodeConstruct.OWLSome(
                            // We can NOT use a named version of hasElement here.
                            // It does not work.
                            //  (Probably it will always be assumed to be different from the other inversions of hasElement)
                            tripleCollector.addConstruct(
                                TripleCollector.BlankNodeConstruct.OWLInverseProperty(URIs.java.hasElement)
                            ),
                            tripleCollector.addConstruct(
                                TripleCollector.BlankNodeConstruct.OWLOneOf.fromURIs(
                                    listOf(containerURI)
                                )
                            )
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
                        NodeFactory.createLiteralByValue(idx, XSDDatatype.XSDint)
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
                            tripleCollector.addConstruct(
                                TripleCollector.BlankNodeConstruct.OWLObjectCardinalityRestriction(
                                    onPropertyUri = URIs.java.hasSuccessor,
                                    onClassUri = URIs.java.SequenceElement,
                                    cardinality = TripleCollector.BlankNodeConstruct.CardinalityType.Exactly(0)
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

            fun addPrimitiveWrapperObject(
                objectURI: String,
                objectReference: ObjectReference,
                mirrorConstructor: (ObjectReference, ThreadReference) -> PrimitiveWrapperMirror<*, *>
            ) {
                try {
                    val mirror = mirrorConstructor(objectReference, buildParameters.jvmState.pausedThread)
                    val valueMirror = mirror.valueMirror()
                    val valueNode = valueMapper.map(valueMirror)

                    if (valueNode != null) {
                        tripleCollector.addStatement(
                            objectURI,
                            URIs.java.hasPlainValue,
                            valueNode
                        )
                    }
                } catch (e: MirroringError) {
                    logger.error(e.message)
                }
            }

            fun addPlainObject(objectURI: String, objectReference: ObjectReference, referenceType: ReferenceType) {
                if (referenceType is ClassType) {
                    addFields(objectURI, objectReference, referenceType)

                    if (referenceType.implementsInterface("java.lang.Iterable")) {
                        addIterableSequence(objectURI, objectReference)
                    }

                    // If this the object is of a wrapper class for a primitive value, we extract the primitive value and directly
                    // represent it in the knowledge base
                    val wrapperObjectCasesX: Map<String, (ObjectReference, ThreadReference) -> PrimitiveWrapperMirror<*, *>> =
                        mapOf(
                            "java.lang.Byte" to ::ByteWrapperMirror,
                            "java.lang.Short" to ::ShortWrapperMirror,
                            "java.lang.Integer" to ::IntegerWrapperMirror,
                            "java.lang.Long" to ::LongWrapperMirror,
                            "java.lang.Float" to ::FloatWrapperMirror,
                            "java.lang.Double" to ::DoubleWrapperMirror,
                            "java.lang.Character" to ::CharacterWrapperMirror,
                        )
                    for ((wrapperClassName, mirrorConstructor) in wrapperObjectCasesX) {
                        if (referenceType.extendsClass(wrapperClassName)) {
                            addPrimitiveWrapperObject(
                                objectURI,
                                objectReference,
                                mirrorConstructor
                            )
                        }
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

                /**
                 * FIXME: Talk about this with SIRIUS
                 *
                 * Usability feature: During debugging, we want to give developers easy, human-readable access to
                 * java.lang.String values.
                 * Therefore, we define the java:hasPlainValue data property for Java strings.
                 * This leaves only the question, which datatype to use:
                 *
                 * The OWL 2 datatype map (https://www.w3.org/TR/owl2-syntax/#Datatype_Maps) offers some types to store
                 * strings, e.g. xsd:string.
                 *
                 * However, none of these types can store arbitrary bytes like java.lang.String can, e.g.
                 * new String(new byte[] { 0 }).
                 * For example, xsd:string may only contain characters matching the XML Char production:
                 *  	Char	   ::=   	[#x1-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
                 *
                 * See also
                 * * https://www.w3.org/TR/xmlschema11-2/#string
                 * * https://www.w3.org/TR/xml11/#NT-Char
                 *
                 * The HermiT reasoner will check this and throw exceptions for strings that contain zero bytes etc.
                 * (We noticed this, when running the performance case study on DaCapo lusearch)
                 *
                 * It seems rdf:PlainLiteral would support the entire range of values, since it supports all characters
                 * from the unicode value set:
                 *
                 * * https://www.w3.org/TR/2012/REC-rdf-plain-literal-20121211/
                 * * https://www.w3.org/TR/rdf-concepts/#dfn-plain-literal
                 * * https://www.w3.org/TR/rdf-concepts/#dfn-lexical-form
                 *
                 * However, apparently rdf:PlainLiteral is not part of the OWL 2 datatype map and HermiT will
                 * automatically type plain literals with xsd:string.
                 *
                 * This leaves only the binary representation types xsd:hexBinary and xsd:base64Binary.
                 * However, using these would defeat the purpose of making string values easily accessible and
                 * human-readable.
                 *
                 * Therefore, we decided, to only set the java:hasPlainValue property for strings which match the
                 * value restrictions of xsd:string.
                 * For every other string, we make it clear that there is no plain representation by restricting the
                 * cardinality of java:hasPlainValue to 0.
                 *
                 * Btw. there is some additional literature on storing binary data in rdf: There is some literature on storing arbitrary binary data in RDF: https://books.google.de/books?id=AKiPuLrMFl8C&pg=PA47&lpg=PA47&dq=RDF+literals+binary+data&source=bl&ots=KJCgFRDKiF&sig=ACfU3U22tEGXlJCBtDMNmZz0o_LTUgp69w&hl=en&sa=X&ved=2ahUKEwiz67if0a_2AhWDtqQKHanDB94Q6AF6BAgnEAM#v=onepage&q=RDF%20literals%20binary%20data&f=false
                 */
                val stringValue = stringReference.value()
                // The check provided by Jena does not seem to do much of anything:
                // if (XSDDatatype.XSDstring.isValidValue(stringValue)) {
                // So we use the same check as HermiT:
                val checker = Datatypes.get("string")
                if (checker.run(stringValue)) {
                    tripleCollector.addStatement(
                        objectURI,
                        URIs.java.hasPlainValue,
                        NodeFactory.createLiteralByValue(
                            stringReference.value(), // FIXME: Usually some symbols, e.g. <, &, " must be escaped in some situations. Jena seems to do it in some cases, but we should check this
                            XSDDatatype.XSDstring
                        )
                    )
                } else {
                    tripleCollector.addStatement(
                        objectURI,
                        URIs.rdf.type,
                        tripleCollector.addConstruct(
                            TripleCollector.BlankNodeConstruct.OWLDataCardinalityRestriction(
                                onPropertyUri = URIs.java.hasPlainValue,
                                cardinality = TripleCollector.BlankNodeConstruct.CardinalityType.Exactly(0)
                            )
                        )
                    )
                }
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
                    NodeFactory.createLiteralByValue(
                        objectReference.uniqueID(),
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

                // Should we close all Java reference type?
                if (buildParameters.limiter.settings.closeReferenceTypes) {
                    for (referenceType in buildParameters.jvmState.pausedThread.virtualMachine().allClasses()) {
                        val typeIri = URIs.prog.genReferenceTypeURI(referenceType)
                        // val instances = referenceType.instances(Long.MAX_VALUE)
                        val instanceIris = allObjects
                            .filter { it.referenceType() == referenceType }
                            .map { URIs.run.genObjectURI(it) }

                        // If so, declare each type equivalent to a nominal containing all its instances
                        tripleCollector.addStatement(
                            typeIri,
                            URIs.owl.equivalentClass,
                            tripleCollector.addConstruct(
                                TripleCollector.BlankNodeConstruct.OWLOneOf.fromURIs(instanceIris)
                            )
                        )
                    }
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