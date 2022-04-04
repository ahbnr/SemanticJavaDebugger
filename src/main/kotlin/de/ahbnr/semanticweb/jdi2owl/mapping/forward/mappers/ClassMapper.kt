package de.ahbnr.semanticweb.jdi2owl.mapping.forward.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.OntURIs
import de.ahbnr.semanticweb.jdi2owl.mapping.datatypes.JavaAccessModifierDatatype
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.BuildParameters
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.IMapper
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.JavaType
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.LocalVariableInfo
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.MethodInfo
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.TripleCollector
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
        private val buildParameters: BuildParameters
    ) : GraphBase(), KoinComponent {
        private val URIs: OntURIs by inject()
        private val logger: Logger by inject()

        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)

            fun addReferenceOrNullClass(referenceTypeURI: String) =
                de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.addReferenceOrNullClass(
                    referenceTypeURI,
                    tripleCollector,
                    URIs
                )

            /**
             * Some classes might not have been loaded by the JVM yet and are only known by name until now.
             * We reflect these incomplete types in the knowledge graph by typing them with java:UnloadedType
             *
             * See also https://docs.oracle.com/en/java/javase/11/docs/api/jdk.jdi/com/sun/jdi/ClassNotLoadedException.html
             */
            fun addUnloadedType(typeName: String) {
                if (buildParameters.limiter.canReferenceTypeBeSkipped(typeName))
                    return

                val subject = URIs.prog.genUnloadedTypeURI(typeName)

                // TODO: Check if we already added a triple for this unloaded type
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

                // all unloaded types must be reference types
                // and thus inherit from java.lang.Object
                tripleCollector.addStatement(
                    subject,
                    URIs.rdfs.subClassOf,
                    URIs.prog.java_lang_Object
                )
            }

            fun addField(classURI: String, field: Field) {
                if (buildParameters.limiter.canFieldBeSkipped(field)) {
                    return
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
                    classURI,
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

                            if (!field.isStatic) {
                                // We force all individuals of the class to implement the field
                                // FIXME: This creates a lot of subClass axioms. Inefficient.
                                //   It is a form of closure axiom. Should we really enforce this?
                                //   It does create reasoning overhead
                                // tripleCollector.addStatement(
                                //     classURI,
                                //     URIs.rdfs.subClassOf,
                                //     tripleCollector.addConstruct(
                                //         TripleCollector.BlankNodeConstruct.OWLSome(
                                //             fieldURI,
                                //             addReferenceOrNullClass(URIs.prog.genReferenceTypeURI(fieldType.type))
                                //         )
                                //     )
                                // )
                            }
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

                            if (!field.isStatic) {
                                // We force all individuals of the class to implement these fields
                                // FIXME: This creates a lot of subClass axioms. Inefficient.
                                //   It is a form of closure axiom. Should we really enforce this?
                                //   It does create reasoning overhead
                                // tripleCollector.addStatement(
                                //     classURI,
                                //     URIs.rdfs.subClassOf,
                                //     tripleCollector.addConstruct(
                                //         TripleCollector.BlankNodeConstruct.OWLSome(
                                //             fieldURI,
                                //             NodeFactory.createURI(datatypeURI)
                                //         )
                                //     )
                                // )
                            }
                        }
                        else -> {
                            logger.error("Encountered unknown kind of type: ${fieldType.type}.")
                        }
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
                }

                tripleCollector.addStatement(
                    fieldURI,
                    URIs.java.isStatic,
                    NodeFactory.createLiteralByValue(field.isStatic, XSDDatatype.XSDboolean)
                )

                if (field.isStatic) {
                    // static field instances are defined for the class itself, not the instances
                    tripleCollector.addStatement(
                        fieldURI,
                        URIs.rdfs.domain,
                        // Punning: We treat the class as an individual
                        tripleCollector.addConstruct(
                            TripleCollector.BlankNodeConstruct.OWLOneOf.fromURIs(
                                listOf(
                                    classURI
                                )
                            )
                        )
                    )
                } else {
                    // an instance field is a thing defined for every object instance of the class concept via rdfs:domain
                    // (this also drives some implications, e.g. if there exists a field, there must also be some class it belongs to etc)
                    tripleCollector.addStatement(
                        fieldURI,
                        URIs.rdfs.domain,
                        classURI
                    )
                }

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
                    addField(classSubject, field)
                }
            }

            fun addVariableDeclaration(
                variable: LocalVariableInfo,
                methodSubject: String
            ) {
                val variableDeclarationURI = URIs.prog.genVariableDeclarationURI(variable)

                // TODO: Include scope information?

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

                // ...and it is declared at this line:
                val line = variable.getLine()
                if (line != null) {
                    tripleCollector.addStatement(
                        variableDeclarationURI,
                        URIs.java.isAtLine,
                        NodeFactory.createLiteralByValue(line, XSDDatatype.XSDint)
                    )
                }

                // Lets clarify the type of the variable and deal with unloaded types
                val variableType = try {
                    JavaType.LoadedType(variable.jdiLocalVariable.type())
                } catch (e: ClassNotLoadedException) {
                    JavaType.UnloadedType(variable.jdiLocalVariable.typeName())
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
                            else -> logger.error("Encountered unknown kind of type: ${variableType.type}")
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

            fun addVariableDeclarations(methodInfo: MethodInfo, methodSubject: String) {
                for (variable in methodInfo.variables) {
                    addVariableDeclaration(variable, methodSubject)
                }
            }

            fun addMethodLocation(methodInfo: MethodInfo, methodSubject: String) {
                // add declaration location
                val declarationLocation = methodInfo.declarationLocation
                if (declarationLocation != null) {
                    val locationURI = URIs.prog.genLocationURI(declarationLocation)

                    // it *is* a java:Location
                    tripleCollector.addStatement(
                        locationURI,
                        URIs.rdf.type,
                        URIs.java.Location
                    )

                    // its a location of a method
                    tripleCollector.addStatement(
                        methodSubject,
                        URIs.java.isDeclaredAt,
                        locationURI
                    )

                    // set source path
                    tripleCollector.addStatement(
                        locationURI,
                        URIs.java.isAtSourcePath,
                        NodeFactory.createLiteralByValue(
                            declarationLocation.sourcePath,
                            XSDDatatype.XSDstring
                        )
                    )

                    // set line
                    tripleCollector.addStatement(
                        locationURI,
                        URIs.java.isAtLine,
                        NodeFactory.createLiteralByValue(
                            declarationLocation.line,
                            XSDDatatype.XSDint
                        )
                    )
                }

                // add body definition location
                val definitionLocation = methodInfo.definitionLocation
                if (definitionLocation != null) {
                    val locationURI = URIs.prog.genLocationURI(definitionLocation)

                    // it *is* a java:Location
                    tripleCollector.addStatement(
                        locationURI,
                        URIs.rdf.type,
                        URIs.java.Location
                    )

                    tripleCollector.addStatement(
                        methodSubject,
                        URIs.java.isDefinedAt,
                        locationURI
                    )

                    // set source path
                    tripleCollector.addStatement(
                        locationURI,
                        URIs.java.isAtSourcePath,
                        NodeFactory.createLiteralByValue(definitionLocation.sourcePath, XSDDatatype.XSDstring)
                    )

                    // set line
                    tripleCollector.addStatement(
                        locationURI,
                        URIs.java.isAtLine,
                        NodeFactory.createLiteralByValue(definitionLocation.line, XSDDatatype.XSDint)
                    )
                }
            }

            fun addMethod(method: Method, referenceTypeURI: String) {
                if (buildParameters.limiter.canMethodBeSkipped(method))
                    return

                val methodInfo = MethodInfo(method, buildParameters)
                val methodSubject = URIs.prog.genMethodURI(methodInfo)

                // The methodSubject *is* a method
                tripleCollector.addStatement(
                    methodSubject,
                    URIs.rdf.type,
                    URIs.java.Method
                )

                // ...and the class contains the method
                tripleCollector.addStatement(
                    referenceTypeURI,
                    URIs.java.hasMethod,
                    methodSubject
                )

                // access modifiers
                tripleCollector.addStatement(
                    methodSubject,
                    URIs.java.hasAccessModifier,
                    JavaAccessModifierDatatype
                        .AccessModifierLiteral
                        .fromJdiAccessible(methodInfo.jdiMethod)
                        .toNode()
                )

                if (buildParameters.limiter.canMethodDetailsBeSkipped(method)) {
                    return
                }

                // ...and the method declares some variables
                addVariableDeclarations(methodInfo, methodSubject)

                // Where in the source code is the method?
                addMethodLocation(methodInfo, methodSubject)
            }

            fun addMethods(referenceTypeURI: String, referenceType: ReferenceType) {
                for (method in referenceType.methods()) { // inherited methods are not included!
                    addMethod(method, referenceTypeURI)
                }
            }

            fun addClass(classType: ClassType) {
                if (buildParameters.limiter.canReferenceTypeBeSkipped(classType))
                    return

                val classSubject = URIs.prog.genReferenceTypeURI(classType)

                // classSubject is an owl class
                // FIXME: Shouldnt this be implied by being a subClassOf java.lang.Object?
                //   either it is not, or some reasoners / frameworks do not recognize this implication because some
                //   things dont work when this is removed.
                //   For example: The SyntacticLocalityModuleExtractor does not extract OWL class definitions for
                //   the java classes.
                tripleCollector.addStatement(
                    classSubject,
                    URIs.rdf.type,
                    URIs.owl.Class
                )

                // This, as an individual, is a Java Class
                tripleCollector.addStatement(
                    classSubject,
                    URIs.rdf.type,
                    URIs.java.Class
                )

                // But we use Punning, and it is also an OWL class
                // More specifically, all its individuals are also part of the superclass
                //
                // (btw. prog:java.lang.Object is defined as an OWL class in the base ontology)
                val superClass: ClassType? = classType.superclass()
                if (superClass != null && !buildParameters.limiter.canReferenceTypeBeSkipped(superClass)) {
                    tripleCollector.addStatement(
                        classSubject,
                        URIs.rdfs.subClassOf,
                        URIs.prog.genReferenceTypeURI(superClass)
                    )
                } else if (classType.name() != "java.lang.Object") {
                    tripleCollector.addStatement(
                        classSubject,
                        URIs.rdfs.subClassOf,
                        URIs.prog.java_lang_Object
                    )
                }

                // https://docs.oracle.com/javase/specs/jls/se11/html/jls-4.html#jls-4.10.2
                val superInterfaces =
                    classType.interfaces().filterNot { buildParameters.limiter.canReferenceTypeBeSkipped(it) }
                for (superInterface in superInterfaces) {
                    tripleCollector.addStatement(
                        classSubject,
                        URIs.rdfs.subClassOf,
                        URIs.prog.genReferenceTypeURI(superInterface)
                    )
                }

                // FIXME: why do Kamburjan et. al. use subClassOf and prog:Object here?
                //  Also: Classes are also objects in Java. However, I moved this to the object mapper
                // tripleCollector.addStatement(
                //     classSubject,
                //     URIs.rdfs.subClassOf,
                //     URIs.prog.Object
                // )

                // Define accessibility
                tripleCollector.addStatement(
                    classSubject,
                    URIs.java.hasAccessModifier,
                    JavaAccessModifierDatatype
                        .AccessModifierLiteral
                        .fromJdiAccessible(classType)
                        .toNode()
                )

                addMethods(classSubject, classType)
                addFields(classSubject, classType)
            }

            fun addArrayType(arrayType: ArrayType) {
                if (buildParameters.limiter.canReferenceTypeBeSkipped(arrayType)) {
                    return
                }

                val arrayTypeURI = URIs.prog.genReferenceTypeURI(arrayType)

                tripleCollector.addStatement(
                    arrayTypeURI,
                    URIs.rdf.type,
                    URIs.owl.Class
                )

                // this, as an individual, is an array:
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

                // Arrays are also a class (punning) where all member individuals are
                // members of
                //    the class Object[] if the component type is a reference type
                //    the interfaces Cloneable and Serializable if the component type is a primitive type
                // and some more supertypes, see https://docs.oracle.com/javase/specs/jls/se11/html/jls-4.html#jls-4.10.3
                //
                // We define Object[] and the synthetic PrimitiveArray class in the base ontology.
                // There, additional appropriate OWL superclasses like the above interfaces are already associated.
                when (componentType) {
                    is JavaType.LoadedType -> {
                        when (componentType.type) {
                            is ReferenceType ->
                                tripleCollector.addStatement(
                                    arrayTypeURI,
                                    URIs.rdfs.subClassOf,
                                    URIs.prog.`java_lang_Object%5B%5D`
                                )
                            is PrimitiveType ->
                                tripleCollector.addStatement(
                                    arrayTypeURI,
                                    URIs.rdfs.subClassOf,
                                    URIs.java.PrimitiveArray
                                )
                            else -> {
                                logger.error("Encountered unknown kind of type: ${componentType.type}")
                            }
                        }
                    }

                    is JavaType.UnloadedType -> {
                        addUnloadedType(componentType.typeName)

                        tripleCollector.addStatement(
                            arrayTypeURI,
                            URIs.rdfs.subClassOf,
                            URIs.java.UnloadedTypeArray
                        )
                    }
                }
            }

            fun addInterface(interfaceType: InterfaceType) {
                if (buildParameters.limiter.canReferenceTypeBeSkipped(interfaceType))
                    return

                val interfaceURI = URIs.prog.genReferenceTypeURI(interfaceType)

                tripleCollector.addStatement(
                    interfaceURI,
                    URIs.rdf.type,
                    URIs.owl.Class
                )

                // This, as an individual, is a Java Interface
                tripleCollector.addStatement(
                    interfaceURI,
                    URIs.rdf.type,
                    URIs.java.Interface
                )

                val superInterfaces = interfaceType.superinterfaces().filterNot {
                    buildParameters.limiter.canReferenceTypeBeSkipped(it)
                }

                if (superInterfaces.isEmpty()) {
                    // If an interface has no direct superinterface, then its java.lang.Object is a direct supertype
                    // https://docs.oracle.com/javase/specs/jls/se11/html/jls-4.html#jls-4.10.2
                    tripleCollector.addStatement(
                        interfaceURI,
                        URIs.rdfs.subClassOf,
                        URIs.prog.java_lang_Object
                    )
                } else {
                    for (superInterface in superInterfaces) {
                        tripleCollector.addStatement(
                            interfaceURI,
                            URIs.rdfs.subClassOf,
                            URIs.prog.genReferenceTypeURI(superInterface)
                        )
                    }
                }

                addMethods(interfaceURI, interfaceType)
            }

            fun addReferenceTypes() {
                val allReferenceTypes = buildParameters.jvmState.pausedThread.virtualMachine().allClasses()

                for (referenceType in allReferenceTypes) {
                    when (referenceType) {
                        is ClassType -> addClass(referenceType)
                        is ArrayType -> addArrayType(referenceType)
                        is InterfaceType -> addInterface(referenceType)
                    }
                }
            }

            addReferenceTypes()

            return tripleCollector.buildIterator()
        }
    }

    override fun extendModel(buildParameters: BuildParameters, outputModel: Model) {
        val graph = Graph(buildParameters)

        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }
}