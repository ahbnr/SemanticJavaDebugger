package de.ahbnr.semanticweb.java_debugger.rdf.mapping.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.*
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.utils.TripleCollector
import org.apache.jena.atlas.lib.IRILib
import org.apache.jena.graph.Node_URI
import org.apache.jena.graph.Triple
import org.apache.jena.graph.impl.GraphBase
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.util.iterator.ExtendedIterator
import org.apache.jena.util.iterator.NullIterator

sealed class JavaType {
    data class LoadedType(val type: Type): JavaType()
    data class UnloadedType(val typeName: String): JavaType()
}

class ClassMapper(
    private val ns: Namespaces
): IMapper {
    private class Graph(
        private val vm: VirtualMachine,
        private val thread: ThreadReference,
        private val ns: Namespaces
    ): GraphBase() {
        override fun graphBaseFind(triplePattern: Triple): ExtendedIterator<Triple> {
            // Guard
            if (triplePattern.subject is Node_URI && triplePattern.subject.nameSpace != ns.prog) {
                return NullIterator()
            }

            // Guard
            if (triplePattern.predicate is Node_URI){
                val possiblePredicates = mutableListOf(
                    "${ns.rdf}type",
                    "${ns.java}hasField",
                    "${ns.rdfs}domain",
                    "${ns.java}hasMethod",
                    "${ns.rdfs}subClassOf"
                )

                val anyEqual = possiblePredicates.any { it == triplePattern.predicate.uri }
                if (!anyEqual) return NullIterator()
            }

            // Guard
            if (triplePattern.getObject() is Node_URI){
                val possibleObjectPrefixes = mutableListOf(ns.java, ns.owl, ns.prog)
                val anyEqual = possibleObjectPrefixes.any { it == triplePattern.getObject().nameSpace }
                if (!anyEqual) return NullIterator()
            }

            val tripleCollector = TripleCollector(triplePattern)

            /**
             * Some classes might not have been loaded by the JVM yet and are only known by name until now.
             * We reflect these incomplete types in the knowledge graph by typing them with java:UnloadedType
             *
             * See also https://docs.oracle.com/en/java/javase/11/docs/api/jdk.jdi/com/sun/jdi/ClassNotLoadedException.html
             */
            fun addUnloadedType(typeName: String) {
                val subject = genUnloadedTypeURI(typeName, ns)

                // FIXME: Check if we already added a triple for this unloaded type
                tripleCollector.addStatement(
                    subject,
                    ns.rdf + "type",
                    ns.java + "UnloadedType"
                )

                // it is also an owl class
                // TODO: Why? Check model
                tripleCollector.addStatement(
                    subject,
                    ns.rdf + "type",
                    ns.owl + "Class"
                )
            }

            fun addField(classSubject: String, classType: ClassType, field: Field) {
                // A field is a property (of a class instance).
                // Hence, we model it as a property in the ontology
                val fieldPropertyName = genFieldPropertyURI(classType, field, ns)

                // Guard
                if (triplePattern.subject is Node_URI){
                    if (triplePattern.subject.uri != classSubject && triplePattern.subject.uri != fieldPropertyName) {
                        return;
                    }
                }

                // this field is a java field
                tripleCollector.addStatement(
                    fieldPropertyName,
                    ns.rdf + "type",
                    ns.java + "Field"
                )

                // and it is part of the class
                tripleCollector.addStatement(
                    classSubject,
                    ns.java + "hasField",
                    fieldPropertyName
                )

                // the field is a thing defined for every object instance of the class concept via rdfs:domain
                // (this also drives some implications, e.g. if there exists a field, there must also be some class it belongs to etc)
                // FIXME: Clearify the use of rdfs:domain
                tripleCollector.addStatement(
                    fieldPropertyName,
                    ns.rdfs + "domain",
                    classSubject
                )

                // Now we need to clarify the type of the field
                val fieldType = try {
                    JavaType.LoadedType(field.type())
                }

                catch (e: ClassNotLoadedException) {
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
                                fieldPropertyName,
                                ns.rdf + "type",
                                ns.owl + "ObjectProperty"
                            )

                            // it is even a functional property: https://www.w3.org/TR/owl-ref/ (4.3.1)
                            // For every instance of the field (there is only one for the object) there is exactly one property value
                            tripleCollector.addStatement(
                                fieldPropertyName,
                                ns.rdf + "type",
                                ns.owl + "FunctionalProperty"
                            )

                            // We now restrict the kind of values the field property can link to, that is, we
                            // set the rdfs:range to the field type

                            val fieldTypeSubject = genClassTypeURI(fieldType.type, ns)
                            tripleCollector.addStatement(
                                fieldPropertyName,
                                ns.rdfs + "range",
                                fieldTypeSubject
                            )
                        }
                        // FIXME: Handle the other cases
                    }

                    is JavaType.UnloadedType -> {
                        addUnloadedType(fieldType.typeName)

                        tripleCollector.addStatement(
                            fieldPropertyName,
                            ns.rdf + "type",
                            ns.owl + "ObjectProperty"
                        )

                        tripleCollector.addStatement(
                            fieldPropertyName,
                            ns.rdf + "type",
                            ns.owl + "FunctionalProperty"
                        )

                        val fieldTypeObject = genUnloadedTypeURI(fieldType.typeName, ns)
                        tripleCollector.addStatement(
                            fieldPropertyName,
                            ns.rdfs + "range",
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

            fun addClassNode(classSubject: String, classType: ClassType) {
                // classSubject is a java class
                tripleCollector.addStatement(
                    classSubject,
                    ns.rdf + "type",
                    ns.java + "Class"
                )

                // classType is an owl class
                tripleCollector.addStatement(
                    classSubject,
                    ns.rdf + "type",
                    ns.owl + "Class"
                )

                // every class is also an object
                tripleCollector.addStatement(
                    classSubject,
                    ns.rdfs + "subClassOf",
                    ns.prog + "Object"
                )

                addFields(classSubject, classType)
            }

            fun addClasses() {
                val allReferenceTypes = vm.allClasses()

                for (referenceType in allReferenceTypes) {
                    when (referenceType) {
                        is ClassType -> {
                            val classSubject = genClassTypeURI(referenceType, ns)

                            if (referenceType.name().startsWith("java") || referenceType.name().startsWith("jdk") ||referenceType.name().startsWith("com") ) {
                                continue
                            }

                            addClassNode(classSubject, referenceType)
                        }
                    }
                }
            }

            addClasses()

            return tripleCollector.buildIterator()
        }
    }

    override fun extendModel(vm: VirtualMachine, thread: ThreadReference, outputModel: Model) {
        val graph = Graph(vm, thread, ns)

        val graphModel = ModelFactory.createModelForGraph(graph)

        outputModel.add(graphModel)
    }

    companion object {
        fun genFieldPropertyURI(classType: ClassType, field: Field, ns: Namespaces): String =
            "${ns.prog}${IRILib.encodeUriComponent(classType.name())}_${IRILib.encodeUriComponent(field.name())}"

        fun genClassTypeURI(classType: ClassType, ns: Namespaces): String {
            return ns.prog + IRILib.encodeUriComponent(classType.name())
        }

        fun genUnloadedTypeURI(typeName: String, ns: Namespaces): String {
            return ns.prog + IRILib.encodeUriComponent(typeName)
        }

        /**
        /**
         * Type names may contain characters not allowed in URI fragments or with special meaning, e.g. [] in `java.security.Permission[]`
         *
         * https://en.wikipedia.org/wiki/URI_fragment
         * https://datatracker.ietf.org/doc/html/rfc3986/#section-3.5
         *
         * This method will properly encode them.
         */
        fun typeNameToURIFragment(className: String): String {
            /**
             * The grammar for a fragment is:
             *       fragment    = *( pchar / "/" / "?" )
             * using this BNF syntax: https://datatracker.ietf.org/doc/html/rfc2234
             *
             * pchar is defined as
             *       pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
             *
             * These characters are unreserved: https://datatracker.ietf.org/doc/html/rfc3986/#section-2.3
             * And everything else must be encoded using percent encoding: https://datatracker.ietf.org/doc/html/rfc3986/#section-2.1
             *
             * The Java 11 type grammar is specified here:
             * https://docs.oracle.com/javase/specs/jls/se11/html/jls-4.html
             *
             * This can get complex, we rely on Apache Jena to safely encode:
             */
            return URIref.encode(className) // FIXME: Verify this is working
        }
         //FIXME: What about unicode?
        **/
    }
}