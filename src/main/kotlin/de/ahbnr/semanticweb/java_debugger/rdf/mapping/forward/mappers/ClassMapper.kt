package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.IMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.AbsentInformationPackages
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

private sealed class JavaType {
    data class LoadedType(val type: Type) : JavaType()
    data class UnloadedType(val typeName: String) : JavaType()
}

class ClassMapper : IMapper {
    private class Graph(
        private val jvmState: JvmState
    ) : GraphBase(), KoinComponent {
        private val URIs: OntURIs by inject()
        private val logger: Logger by inject()

        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            val tripleCollector = TripleCollector(triplePattern)

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

            fun addField(classSubject: String, classType: ClassType, field: Field) {
                // A field is a property (of a class instance).
                // Hence, we model it as a property in the ontology
                val fieldURI = URIs.prog.genFieldURI(classType, field)

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

                // the field is a thing defined for every object instance of the class concept via rdfs:domain
                // (this also drives some implications, e.g. if there exists a field, there must also be some class it belongs to etc)
                tripleCollector.addStatement(
                    fieldURI,
                    URIs.rdfs.domain,
                    classSubject
                )

                // Now we need to clarify the type of the field
                val fieldType = try {
                    JavaType.LoadedType(field.type())
                } catch (e: ClassNotLoadedException) {
                    JavaType.UnloadedType(field.typeName())
                }

                when (fieldType) {
                    is JavaType.LoadedType -> when (fieldType.type) {
                        is ClassType -> {
                            // Since the Java field is of a class type here, it must be an ObjectProperty,
                            // (https://www.w3.org/TR/owl-ref/#ObjectProperty-def)
                            // that is, a property that links individuals to individuals
                            // (here: Java class instances (parent object) to Java class instances (field value))
                            tripleCollector.addStatement(
                                fieldURI,
                                URIs.rdf.type,
                                URIs.owl.ObjectProperty
                            )

                            // it is even a functional property: https://www.w3.org/TR/owl-ref/ (4.3.1)
                            // For every instance of the field (there is only one for the object) there is exactly one property value
                            tripleCollector.addStatement(
                                fieldURI,
                                URIs.rdf.type,
                                URIs.owl.FunctionalProperty
                            )

                            // We now restrict the kind of values the field property can link to, that is, we
                            // set the rdfs:range to the field type
                            val fieldTypeSubject = URIs.prog.genReferenceTypeURI(fieldType.type)
                            tripleCollector.addStatement(
                                fieldURI,
                                URIs.rdfs.range,
                                // every reference type field can either be an instance or null:
                                // fieldTypeSubject ∪ { java:null }
                                // [ owl:unionOf ( fieldTypeSubject [ owl:oneOf ( java:null ) ] ) ] .
                                tripleCollector.addCollection(
                                    TripleCollector.CollectionObject.OWLUnion(
                                        listOf(
                                            NodeFactory.createURI(fieldTypeSubject),

                                            tripleCollector.addCollection(
                                                TripleCollector.CollectionObject.OWLOneOf.fromURIs(listOf(URIs.java.`null`))
                                            )
                                        )
                                    )
                                )
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
                            URIs.rdf.type,
                            URIs.owl.FunctionalProperty
                        )

                        val fieldTypeObject = URIs.prog.genUnloadedTypeURI(fieldType.typeName)
                        tripleCollector.addStatement(
                            fieldURI,
                            URIs.rdfs.range,
                            fieldTypeObject
                        )
                    }
                }
            }

            fun addFields(classSubject: String, classType: ClassType) {
                // Note, that "fields()" gives us the fields of the type in question only, not the fields of superclasses
                for (field in classType.fields()) {
                    addField(classSubject, classType, field)
                }
            }

            fun addVariableDeclaration(
                variable: LocalVariable,
                methodSubject: String,
                method: Method,
                classType: ClassType
            ) {
                val variableDeclarationSubject = URIs.prog.genVariableDeclarationURI(variable, method, classType)

                // it *is* a VariableDeclaration
                tripleCollector.addStatement(
                    variableDeclarationSubject,
                    URIs.rdf.type,
                    URIs.java.VariableDeclaration
                )

                // ...and it is declared by the surrounding method
                tripleCollector.addStatement(
                    methodSubject,
                    URIs.java.declaresVariable,
                    variableDeclarationSubject
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

                // ...and the method declares some variables
                addVariableDeclarations(methodSubject, method, classType)

                // Where in the source code is the method?
                addMethodLocation(method, methodSubject)
            }

            fun addMethods(classSubject: String, classType: ClassType) {
                for (method in classType.methods()) { // inherited methods are not included!
                    addMethod(method, classSubject, classType)
                }
            }

            fun addClass(classType: ClassType) {
                val classSubject = URIs.prog.genReferenceTypeURI(classType)

                // classSubject is a java class
                tripleCollector.addStatement(
                    classSubject,
                    URIs.rdf.type,
                    URIs.java.Class
                )

                // classSubject is an owl class
                tripleCollector.addStatement(
                    classSubject,
                    URIs.rdf.type,
                    URIs.owl.Class
                )

                // every class is also an object (FIXME: subClassOf??)
                tripleCollector.addStatement(
                    classSubject,
                    URIs.rdfs.subClassOf,
                    URIs.prog.Object
                )

                addMethods(classSubject, classType)
                addFields(classSubject, classType)
            }

            fun addClasses() {
                val allReferenceTypes = jvmState.pausedThread.virtualMachine().allClasses()

                for (referenceType in allReferenceTypes) {
                    when (referenceType) {
                        is ClassType -> {
                            //if (referenceType.name().startsWith("java") || referenceType.name().startsWith("jdk") ||referenceType.name().startsWith("com") ) {
                            //    continue
                            //}

                            addClass(referenceType)
                        }
                        // FIXME handle other reference types
                    }
                }
            }

            addClasses()

            return tripleCollector.buildIterator()
        }
    }

    override fun extendModel(jvmState: JvmState, outputModel: Model) {
        val graph = Graph(jvmState)

        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }
}