package de.ahbnr.semanticweb.java_debugger.rdf.mapping.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.*
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.utils.addStatement
import org.apache.jena.atlas.lib.IRILib
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ResourceFactory

sealed class JavaType {
    data class LoadedType(val type: Type): JavaType()
    data class UnloadedType(val typeName: String): JavaType()
}

class ClassMapper(
    private val ns: Namespaces
): IMapper {
    private fun addField(classSubject: String, classType: ClassType, field: Field, outputModel: Model) {
        // A field is a property (of a class instance).
        // Hence, we model it as a property in the ontology
        val fieldPropertyName = genFieldPropertyURI(classType, field, ns)

        // this field is a java field
        addStatement(outputModel,
            fieldPropertyName,
            ns.rdf + "type",
            ns.java + "Field"
        )

        // and it is part of the class
        addStatement(outputModel,
            classSubject,
            ns.java + "hasField",
            fieldPropertyName
        )

        // the field is a thing defined for every object instance of the class concept via rdfs:domain
        // (this also drives some implications, e.g. if there exists a field, there must also be some class it belongs to etc)
        // FIXME: Clearify the use of rdfs:domain
        addStatement(outputModel,
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
                    addStatement(outputModel,
                        fieldPropertyName,
                        ns.rdf + "type",
                        ns.owl + "ObjectProperty"
                    )

                    // it is even a functional property: https://www.w3.org/TR/owl-ref/ (4.3.1)
                    // For every instance of the field (there is only one for the object) there is exactly one property value
                    addStatement(outputModel,
                        fieldPropertyName,
                        ns.rdf + "type",
                        ns.owl + "FunctionalProperty"
                    )

                    // We now restrict the kind of values the field property can link to, that is, we
                    // set the rdfs:range to the field type

                    val fieldTypeSubject = genClassTypeURI(fieldType.type)
                    addStatement(outputModel,
                        fieldPropertyName,
                        ns.rdfs + "range",
                        fieldTypeSubject
                    )
                }
                // FIXME: Handle the other cases
            }

            is JavaType.UnloadedType -> {
                addUnloadedType(fieldType.typeName, outputModel)

                addStatement(outputModel,
                    fieldPropertyName,
                    ns.rdf + "type",
                    ns.owl + "ObjectProperty"
                )

                addStatement(outputModel,
                    fieldPropertyName,
                    ns.rdf + "type",
                    ns.owl + "FunctionalProperty"
                )

                val fieldTypeObject = genUnloadedTypeURI(fieldType.typeName)
                addStatement(outputModel,
                    fieldPropertyName,
                    ns.rdfs + "range",
                    fieldTypeObject
                )
            }
        }
    }

    private fun addFields(classSubject: String, classType: ClassType, outputModel: Model) {
        // Note, that "fields()" gives us the fields of the type in question only, not the fields of superclasses
        for (field in classType.fields()) {
            addField(classSubject, classType, field, outputModel)
        }
    }

    /**
     * Some classes might not have been loaded by the JVM yet and are only known by name until now.
     * We reflect these incomplete types in the knowledge graph by typing them with java:UnloadedType
     *
     * See also https://docs.oracle.com/en/java/javase/11/docs/api/jdk.jdi/com/sun/jdi/ClassNotLoadedException.html
     */
    private fun addUnloadedType(typeName: String, outputModel: Model) {
        val subject = genUnloadedTypeURI(typeName)

        // FIXME: Verify that this check works correctly. Maybe check efficiency
        if (!outputModel.containsResource(ResourceFactory.createResource(subject))) {
            addStatement(outputModel,
                subject,
                ns.rdf + "type",
                ns.java + "UnloadedType"
            )

            // it is also an owl class
            // TODO: Why? Check model
            addStatement(outputModel,
                subject,
                ns.rdf + "type",
                ns.owl + "Class"
            )
        }
    }

    private fun addClassNode(classSubject: String, classType: ClassType, outputModel: Model) {
        // classSubject is a java class
        addStatement(outputModel,
            classSubject,
            ns.rdf + "type",
            ns.java + "Class"
        )

        // classType is an owl class
        addStatement(outputModel,
            classSubject,
            ns.rdf + "type",
            ns.owl + "Class"
        )

        // every class is also an object
        addStatement(outputModel,
            classSubject,
            ns.rdfs + "subClassOf",
            ns.prog + "Object"
        )

        addFields(classSubject, classType, outputModel)
    }

    override fun extendModel(vm: VirtualMachine, thread: ThreadReference, outputModel: Model) {
        val allReferenceTypes = vm.allClasses()

        for (referenceType in allReferenceTypes) {
            when (referenceType) {
                is ClassType -> {
                    val classSubject = genClassTypeURI(referenceType)

                    addClassNode(classSubject, referenceType, outputModel)
                }
            }
        }
    }

    private fun genClassTypeURI(classType: ClassType): String {
        return ns.prog + IRILib.encodeUriComponent(classType.name())
    }

    private fun genUnloadedTypeURI(typeName: String): String {
        return ns.prog + IRILib.encodeUriComponent(typeName)
    }

    companion object {
        fun genFieldPropertyURI(classType: ClassType, field: Field, ns: Namespaces): String =
            "${ns.prog}${IRILib.encodeUriComponent(classType.name())}_${IRILib.encodeUriComponent(field.name())}"

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