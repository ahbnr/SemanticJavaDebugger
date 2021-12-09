package de.ahbnr.semanticweb.java_debugger.rdf.mapping

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.LocalVariableInfo
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.LocationInfo
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils.MethodInfo
import org.apache.jena.atlas.lib.IRILib
import org.apache.jena.datatypes.xsd.XSDDatatype

@Suppress("PropertyName")
class OntURIs(val ns: Namespaces) {
    inner class RdfURIs {
        val type = ns.rdf + "type"

        val List = ns.rdf + "List"
        val first = ns.rdf + "first"
        val rest = ns.rdf + "rest"
        val nil = ns.rdf + "nil"
    }

    val rdf = RdfURIs()

    inner class RdfsURIs {
        val subClassOf = ns.rdfs + "subClassOf"
        val subPropertyOf = ns.rdfs + "subPropertyOf"
        val domain = ns.rdfs + "domain"
        val range = ns.rdfs + "range"
    }

    val rdfs = RdfsURIs()

    inner class OwlURIs {
        val Restriction = ns.owl + "Restriction"
        val onProperty = ns.owl + "onProperty"
        val someValuesFrom = ns.owl + "someValuesFrom"

        val Class = ns.owl + "Class"
        val ObjectProperty = ns.owl + "ObjectProperty"
        val DatatypeProperty = ns.owl + "DatatypeProperty"
        val FunctionalProperty = ns.owl + "FunctionalProperty"
        val cardinality = ns.owl + "cardinality"
        val NamedIndividual = ns.owl + "NamedIndividual"
        val unionOf = ns.owl + "unionOf"
        val oneOf = ns.owl + "oneOf"
    }

    val owl = OwlURIs()

    inner class ShaclURIs {
        val conforms = ns.sh + "conforms"
        val result = ns.sh + "result"
        val focusNode = ns.sh + "focusNode"
        val value = ns.sh + "value"
    }

    val sh = ShaclURIs()

    inner class JavaURIs {
        val UnloadedType = ns.java + "UnloadedType"
        val Class = ns.java + "Class"
        val Method = ns.java + "Method"
        val Field = ns.java + "Field"

        val Interface = ns.java + "Interface"

        val Array = ns.java + "Array"
        val ArrayElement = ns.java + "ArrayElement"
        val `ArrayElement%3CObject%3E` = ns.java + IRILib.encodeUriComponent("ArrayElement<Object>")
        val UnloadedTypeArray = ns.java + "UnloadedTypeArray"
        val PrimitiveArray = ns.java + "PrimitiveArray"
        val PrimitiveArrayElement = ns.java + "PrimitiveArrayElement"
        val hasIndex = ns.java + "hasIndex"
        val hasElement = ns.java + "hasElement"
        val storesPrimitive = ns.java + "storesPrimitive"
        val storesReference = ns.java + "storesReference"

        val VariableDeclaration = ns.java + "VariableDeclaration"

        val Location = ns.java + "Location"

        val hasMethod = ns.java + "hasMethod"
        val hasField = ns.java + "hasField"
        val declaresVariable = ns.java + "declaresVariable"
        val isDefinedAt = ns.java + "isDefinedAt"
        val isDeclaredAt = ns.java + "isDeclaredAt"
        val isAtSourcePath = ns.java + "isAtSourcePath"
        val isAtLine = ns.java + "isAtLine"

        val `null` = ns.java + "null"

        val Object = ns.java + "Object"
        val StackFrame = ns.java + "StackFrame"

        val isAtStackDepth = ns.java + "isAtStackDepth"
        val hasJDWPObjectId = ns.java + "hasJDWPObjectId"

        val hasStringValue = ns.java + "hasStringValue"

        val isStatic = ns.java + "isStatic"

        val hasAccessModifier = ns.java + "hasAccessModifier"
        val AccessModifier = ns.java + "AccessModifier"

        fun genPrimitiveTypeURI(type: PrimitiveType): String? = when (type) {
            is BooleanType -> XSDDatatype.XSDboolean
            is ByteType -> XSDDatatype.XSDbyte
            is CharType -> XSDDatatype.XSDunsignedShort
            is DoubleType -> XSDDatatype.XSDdouble
            is FloatType -> XSDDatatype.XSDfloat
            is IntegerType -> XSDDatatype.XSDint
            is LongType -> XSDDatatype.XSDlong
            is ShortType -> XSDDatatype.XSDshort
            else -> null
        }?.uri
    }

    val java = JavaURIs()

    inner class ProgURIs {
        val `java_lang_Object%5B%5D` = ns.prog + IRILib.encodeUriComponent("java.lang.Object[]")
        val java_lang_Object = ns.prog + IRILib.encodeUriComponent("java.lang.Object")

        fun genVariableDeclarationURI(variable: LocalVariableInfo): String {
            val referenceType = variable.methodInfo.jdiMethod.declaringType()

            return "${ns.prog}${IRILib.encodeUriComponent(referenceType.name())}.${IRILib.encodeUriComponent(variable.methodInfo.id)}.${
                IRILib.encodeUriComponent(
                    variable.id
                )
            }"
        }

        fun genMethodURI(methodInfo: MethodInfo): String {
            val referenceType = methodInfo.jdiMethod.declaringType()

            return "${ns.prog}${IRILib.encodeUriComponent(referenceType.name())}.${
                IRILib.encodeUriComponent(methodInfo.id)
            }"
        }

        fun genReferenceTypeURI(referenceType: ReferenceType): String {
            return "${ns.prog}${IRILib.encodeUriComponent(referenceType.name())}"
        }

        fun genFieldURI(field: Field): String =
            "${ns.prog}${
                IRILib.encodeUriComponent(
                    field.declaringType().name()
                )
            }.${IRILib.encodeUriComponent(field.name())}"

        fun genUnloadedTypeURI(typeName: String): String {
            return ns.prog + IRILib.encodeUriComponent(typeName)
        }

        fun genLocationURI(locationInfo: LocationInfo): String =
            "${ns.prog}location_${IRILib.encodeUriComponent(locationInfo.id)}"

        fun genTypedHasElementURI(arrayType: ArrayType): String =
            "${ns.prog}hasElement${IRILib.encodeUriComponent("<${arrayType.componentTypeName()}>")}"

        fun genTypedArrayElementURI(arrayType: ArrayType): String =
            "${ns.prog}ArrayElement${IRILib.encodeUriComponent("<${arrayType.componentTypeName()}>")}"

        fun genTypedStoresPrimitiveURI(arrayType: ArrayType): String =
            "${ns.prog}storesPrimitive${IRILib.encodeUriComponent("<${arrayType.componentTypeName()}>")}"

        fun genTypedStoresReferenceURI(arrayType: ArrayType): String =
            "${ns.prog}storesReference${IRILib.encodeUriComponent("<${arrayType.componentTypeName()}>")}"
    }

    val prog = ProgURIs()

    inner class RunURIs {
        fun genFrameURI(frameDepth: Int): String =
            "${ns.run}frame$frameDepth"

        fun genObjectURI(objectReference: ObjectReference): String =
            "${ns.run}object${objectReference.uniqueID()}"

        fun genSizedHasElementURI(arrayReference: ArrayReference): String =
            "${ns.run}hasElement_object${arrayReference.uniqueID()}"

        fun genArrayElementInstanceURI(arrayReference: ArrayReference, index: Int) =
            "${ns.run}element${index}_of_${arrayReference.uniqueID()}"
    }

    val run = RunURIs()


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