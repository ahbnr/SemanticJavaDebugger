package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.IMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.MappingLimiter
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.AbsentInformationPackages
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.JavaType
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

class ClassMapper : IMapper {
    private class Graph(
        private val jvmState: JvmState,
        private val limiter: MappingLimiter
    ) : GraphBase(), KoinComponent {
        private val URIs: OntURIs by inject()
        private val logger: Logger by inject()

        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)

            fun addReferenceOrNullClass(referenceTypeURI: String) =
            // every reference can either be an instance or null:
            // fieldTypeSubject ∪ { java:null }
                // [ owl:unionOf ( fieldTypeSubject [ owl:oneOf ( java:null ) ] ) ] .
                tripleCollector.addCollection(
                    TripleCollector.CollectionObject.OWLUnion(
                        listOf(
                            NodeFactory.createURI(referenceTypeURI),

                            tripleCollector.addCollection(
                                TripleCollector.CollectionObject.OWLOneOf.fromURIs(listOf(URIs.java.`null`))
                            )
                        )
                    )
                )

            /**
             * Some classes might not have been loaded by the JVM yet and are only known by name until now.
             * We reflect these incomplete types in the knowledge graph by typing them with java:UnloadedType
             *
             * See also https://docs.oracle.com/en/java/javase/11/docs/api/jdk.jdi/com/sun/jdi/ClassNotLoadedException.html
             */
            fun addUnloadedType(typeName: String) {
                val subject = URIs.prog.genUnloadedTypeURI(typeName)

                // FIXME: Check if we already added a triple for this unloaded type
                tripleCollector.addStatement(
                    subject,
                    URIs.rdf.type,
                    URIs.java.UnloadedType
                )

                // it is also an owl class
                // TODO: Why? Check model
                tripleCollector.addStatement(
                    subject,
                    URIs.rdf.type,
                    URIs.owl.Class
                )
            }

            fun addField(classSubject: String, field: Field) {
                if (field.isStatic) {
                    return // FIXME: Handle static fields
                }

                // A field is a property (of a class instance).
                // Hence, we model it as a property in the ontology
                val fieldURI = URIs.prog.genFieldURI(field)

                // this field is a java field
                tripleCollector.addStatement(
                    fieldURI,
                    URIs.rdf.type,
                    URIs.java.Field
                )

                // and it is part of the class
                tripleCollector.addStatement(
                    classSubject,
                    URIs.java.hasField,
                    fieldURI
                )

                // Now we need to clarify the type of the field
                val fieldType = try {
                    JavaType.LoadedType(field.type())
                } catch (e: ClassNotLoadedException) {
                    JavaType.UnloadedType(field.typeName())
                }

                // Fields are modeled as properties.
                // This way, the field type can be encoded in the property range.
                // The exact kind of property and ranges now depend on the field type:
                when (fieldType) {
                    is JavaType.LoadedType -> when (fieldType.type) {
                        // "normal" Java classes
                        is ReferenceType -> {
                            // Since the Java field is of a class type here, it must be an ObjectProperty,
                            // (https://www.w3.org/TR/owl-ref/#ObjectProperty-def)
                            // that is, a property that links individuals to individuals
                            // (here: Java class instances (parent object) to Java class instances (field value))
                            tripleCollector.addStatement(
                                fieldURI,
                                URIs.rdf.type,
                                URIs.owl.ObjectProperty
                            )

                            // We now restrict the kind of values the field property can link to, that is, we
                            // set the rdfs:range to the field type
                            tripleCollector.addStatement(
                                fieldURI,
                                URIs.rdfs.range,
                                addReferenceOrNullClass(URIs.prog.genReferenceTypeURI(fieldType.type))
                            )
                        }
                        is PrimitiveType -> {
                            tripleCollector.addStatement(
                                fieldURI,
                                URIs.rdf.type,
                                URIs.owl.DatatypeProperty
                            )

                            val datatypeURI = URIs.java.genPrimitiveTypeURI(fieldType.type)
                            if (datatypeURI == null) {
                                logger.error("Unknown primitive data type: ${fieldType.type}")
                                return
                            }

                            tripleCollector.addStatement(
                                fieldURI,
                                URIs.rdfs.range,
                                datatypeURI
                            )
                        }
                        // FIXME: Handle the other cases
                    }

                    is JavaType.UnloadedType -> {
                        addUnloadedType(fieldType.typeName)

                        tripleCollector.addStatement(
                            fieldURI,
                            URIs.rdf.type,
                            URIs.owl.ObjectProperty
                        )

                        tripleCollector.addStatement(
                            fieldURI,
                            URIs.rdfs.range,
                            URIs.prog.genUnloadedTypeURI(fieldType.typeName)
                        )
                    }
                    // FIXME: Handle the other cases
                }

                // the field is a thing defined for every object instance of the class concept via rdfs:domain
                // (this also drives some implications, e.g. if there exists a field, there must also be some class it belongs to etc)
                tripleCollector.addStatement(
                    fieldURI,
                    URIs.rdfs.domain,
                    classSubject
                )

                // Fields are functional properties.
                // For every instance of the field (there is only one for the object) there is exactly one property value
                tripleCollector.addStatement(
                    fieldURI,
                    URIs.rdf.type,
                    URIs.owl.FunctionalProperty
                )
            }

            fun addFields(classSubject: String, classType: ClassType) {
                // Note, that "fields()" gives us the fields of the type in question only, not the fields of superclasses
                for (field in classType.fields()) {
                    if (!field.isPublic && limiter.isShallow(classType)) {
                        continue
                    }

                    addField(classSubject, field)
                }
            }

            fun addVariableDeclaration(
                variable: LocalVariable,
                methodSubject: String,
                method: Method,
                classType: ClassType
            ) {
                val variableDeclarationURI = URIs.prog.genVariableDeclarationURI(variable, method, classType)

                // it *is* a VariableDeclaration
                tripleCollector.addStatement(
                    variableDeclarationURI,
                    URIs.rdf.type,
                    URIs.java.VariableDeclaration
                )

                // ...and it is declared by the surrounding method
                tripleCollector.addStatement(
                    methodSubject,
                    URIs.java.declaresVariable,
                    variableDeclarationURI
                )

                // Lets clarify the type of the variable and deal with unloaded types
                val variableType = try {
                    JavaType.LoadedType(variable.type())
                } catch (e: ClassNotLoadedException) {
                    JavaType.UnloadedType(variable.typeName())
                }

                // A variable declaration is modeled as a property that relates StackFrames and the variable values.
                // This allows to encode the typing of the variable into the property range.

                // The kind of property and range depend on the variable type:
                when (variableType) {
                    is JavaType.LoadedType -> {
                        when (variableType.type) {
                            is ReferenceType -> {
                                // If its a reference type, then it must be an ObjectProperty
                                tripleCollector.addStatement(
                                    variableDeclarationURI,
                                    URIs.rdf.type,
                                    URIs.owl.ObjectProperty
                                )

                                // ...and the variable property ranges over the reference type of the variable
                                // and the null value:
                                tripleCollector.addStatement(
                                    variableDeclarationURI,
                                    URIs.rdfs.range,
                                    addReferenceOrNullClass(URIs.prog.genReferenceTypeURI(variableType.type))
                                )
                            }
                            is PrimitiveType -> {
                                tripleCollector.addStatement(
                                    variableDeclarationURI,
                                    URIs.rdf.type,
                                    URIs.owl.DatatypeProperty
                                )

                                val datatypeURI = URIs.java.genPrimitiveTypeURI(variableType.type)
                                if (datatypeURI == null) {
                                    logger.error("Unknown primitive data type: ${variableType.type}")
                                    return
                                }

                                tripleCollector.addStatement(
                                    variableDeclarationURI,
                                    URIs.rdfs.range,
                                    datatypeURI
                                )
                            }
                            // FIXME: deal with other cases
                        }
                    }
                    is JavaType.UnloadedType -> {
                        addUnloadedType(variableType.typeName)

                        tripleCollector.addStatement(
                            variableDeclarationURI,
                            URIs.rdf.type,
                            URIs.owl.ObjectProperty
                        )

                        tripleCollector.addStatement(
                            variableDeclarationURI,
                            URIs.rdfs.range,
                            URIs.prog.genUnloadedTypeURI(variableType.typeName)
                        )
                    }
                }

                // Variables are always functional properties
                tripleCollector.addStatement(
                    variableDeclarationURI,
                    URIs.rdf.type,
                    URIs.owl.FunctionalProperty
                )

                // The property domain is a StackFrame
                tripleCollector.addStatement(
                    variableDeclarationURI,
                    URIs.rdfs.domain,
                    URIs.java.StackFrame
                )
            }

            fun addVariableDeclarations(methodSubject: String, method: Method, classType: ClassType) {
                val variables = (
                        if (!method.isAbstract && !method.isNative)
                            try {
                                method.variables()
                            } catch (e: AbsentInformationException) {
                                if (AbsentInformationPackages.none { classType.name().startsWith(it) }) {
                                    logger.debug("Unable to get variables for $method. This can happen for native and abstract methods.")
                                }
                                null
                            }
                        else null)
                    ?: listOf()

                for (variable in variables) {
                    // FIXME: Deal with scopes

                    addVariableDeclaration(variable, methodSubject, method, classType)
                }
            }

            fun addMethodLocation(method: Method, methodSubject: String) {
                val location =
                    method.location() // where is the method executable code defined? May return null for abstract methods

                if (location != null) {
                    val locationSubject = URIs.prog.genLocationURI(location)

                    // it *is* a java:Location
                    tripleCollector.addStatement(
                        locationSubject,
                        URIs.rdf.type,
                        URIs.java.Location
                    )

                    // its the location of a method
                    tripleCollector.addStatement(
                        methodSubject,
                        URIs.java.isDefinedAt,
                        locationSubject
                    )

                    // set source path, if it is known
                    val sourcePath = try {
                        location.sourcePath()
                    } catch (e: AbsentInformationException) {
                        null
                    }
                    if (sourcePath != null) {
                        tripleCollector.addStatement(
                            locationSubject,
                            URIs.java.isAtSourcePath,
                            NodeFactory.createLiteral(sourcePath, XSDDatatype.XSDstring)
                        )
                    }

                    val lineNumber = location.lineNumber()
                    if (lineNumber >= 0) { // -1 indicates that the number is not known
                        tripleCollector.addStatement(
                            locationSubject,
                            URIs.java.isAtLine,
                            NodeFactory.createLiteral(lineNumber.toString(), XSDDatatype.XSDint)
                        )
                    }
                } else if (!method.isAbstract) {
                    logger.error(
                        "${
                            method.declaringType().name()
                        }:${method.name()}: Location of method body could not be determined, even though the method is not abstract."
                    )
                }
            }

            fun addMethod(method: Method, classSubject: String, classType: ClassType) {
                if (!method.isPublic && limiter.isShallow(classType)) {
                    return
                }

                val methodSubject = URIs.prog.genMethodURI(method, classType)

                // The methodSubject *is* a method
                tripleCollector.addStatement(
                    methodSubject,
                    URIs.rdf.type,
                    URIs.java.Method
                )

                // ...and the class contains the method
                tripleCollector.addStatement(
                    classSubject,
                    URIs.java.hasMethod,
                    methodSubject
                )

                if (limiter.isShallow(classType)) {
                    return
                }

                // ...and the method declares some variables
                addVariableDeclarations(methodSubject, method, classType)

                // Where in the source code is the method?
                addMethodLocation(method, methodSubject)
            }

            fun addMethods(classSubject: String, classType: ClassType) {
                for (method in classType.methods()) { // inherited methods are not included!
                    if (!method.isPublic && limiter.isShallow(classType)) {
                        continue
                    }

                    addMethod(method, classSubject, classType)
                }
            }

            fun addClass(classType: ClassType) {
                val classSubject = URIs.prog.genReferenceTypeURI(classType)

                // FIXME: Ensure that we can remove this
                // FIXME: This should be given by transitivity of subClassOf java.lang.Object
                //
                // // classSubject is a java class
                // tripleCollector.addStatement(
                //     classSubject,
                //     URIs.rdf.type,
                //     URIs.java.Class
                // )
                //
                // // classSubject is an owl class
                // tripleCollector.addStatement(
                //     classSubject,
                //     URIs.rdf.type,
                //     URIs.owl.Class
                // )

                // place it in the class hierarchy
                val superClass: ClassType? = classType.superclass()
                if (superClass != null) {
                    tripleCollector.addStatement(
                        classSubject,
                        URIs.rdfs.subClassOf,
                        URIs.prog.genReferenceTypeURI(superClass)
                    )
                }

                // FIXME: why do Kamburjan et. al. use subClassOf and prog:Object here?
                //  Also: Classes are also objects in Java. However, I moved this to the object mapper
                // tripleCollector.addStatement(
                //     classSubject,
                //     URIs.rdfs.subClassOf,
                //     URIs.prog.Object
                // )

                addMethods(classSubject, classType)
                addFields(classSubject, classType)
            }

            fun addArrayType(arrayType: ArrayType) {
                val arrayTypeURI = URIs.prog.genReferenceTypeURI(arrayType)

                tripleCollector.addStatement(
                    arrayTypeURI,
                    URIs.rdf.type,
                    URIs.java.Array
                )

                // Now we need to clarify the type of the array elements
                val componentType = try {
                    JavaType.LoadedType(arrayType.componentType())
                } catch (e: ClassNotLoadedException) {
                    JavaType.UnloadedType(arrayType.componentTypeName())
                }

                when (componentType) {
                    is JavaType.LoadedType -> {
                        val typedArrayElementURI = URIs.prog.genTypedArrayElementURI(componentType.type)
                        when (componentType.type) {
                            is PrimitiveType -> {
                                tripleCollector.addStatement(
                                    typedArrayElementURI,
                                    URIs.rdfs.subClassOf,
                                    URIs.java.PrimitiveArrayElement
                                )

                                // hasElement<type> Relation
                                val typedHasElementURI = URIs.prog.genTypedHasElementURI(componentType.type)
                                tripleCollector.addStatement(
                                    typedHasElementURI,
                                    URIs.rdfs.subPropertyOf,
                                    URIs.java.hasElement
                                )

                                tripleCollector.addStatement(
                                    typedHasElementURI,
                                    URIs.rdfs.domain,
                                    arrayTypeURI
                                )

                                tripleCollector.addStatement(
                                    typedHasElementURI,
                                    URIs.rdfs.range,
                                    typedArrayElementURI
                                )

                                // storesPrimitive Relation
                                val typedStoresPrimitiveURI = URIs.prog.genTypedStoresPrimitiveURI(arrayType)

                                tripleCollector.addStatement(
                                    typedStoresPrimitiveURI,
                                    URIs.rdfs.subPropertyOf,
                                    URIs.java.storesPrimitive
                                )

                                tripleCollector.addStatement(
                                    typedStoresPrimitiveURI,
                                    URIs.rdfs.domain,
                                    typedArrayElementURI
                                )

                                val datatypeURI = URIs.java.genPrimitiveTypeURI(componentType.type)
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
                                    typedArrayElementURI,
                                    URIs.rdfs.subClassOf,
                                    tripleCollector.addCollection(
                                        TripleCollector.CollectionObject.OWLSome(
                                            typedStoresPrimitiveURI, datatypeURI
                                        )
                                    )
                                )
                            }
                            // FIXME: Handle other cases
                        }
                    }
                    else -> Unit // FIXME: Handle case
                }
            }

            fun addReferenceTypes() {
                val allReferenceTypes = jvmState.pausedThread.virtualMachine().allClasses()

                for (referenceType in allReferenceTypes) {
                    if (limiter.isExcluded(referenceType)) {
                        continue
                    }

                    if (
                        referenceType !is ArrayType
                        && !referenceType.isPublic
                        // ^^isPublic can print exception stacktraces for arrays, very annoying. Without this, we could remove the restriction above
                        && limiter.isShallow(referenceType)
                    ) {
                        continue
                    }

                    when (referenceType) {
                        is ClassType -> addClass(referenceType)
                        is ArrayType -> addArrayType(referenceType)
                        // FIXME handle other reference types
                    }
                }
            }

            addReferenceTypes()

            return tripleCollector.buildIterator()
        }
    }

    override fun extendModel(jvmState: JvmState, outputModel: Model, limiter: MappingLimiter) {
        val graph = Graph(jvmState, limiter)

        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }
}