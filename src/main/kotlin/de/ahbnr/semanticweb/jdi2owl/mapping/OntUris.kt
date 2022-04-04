package de.ahbnr.semanticweb.jdi2owl.mapping

import com.sun.jdi.*
import de.ahbnr.semanticweb.jdi2owl.debugging.utils.getFullyQualifiedName
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.LocalVariableInfo
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.LocationInfo
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.MethodInfo
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
        val Thing = ns.owl + "Thing"
        val Nothing = ns.owl + "Nothing"

        val Restriction = ns.owl + "Restriction"
        val onProperty = ns.owl + "onProperty"
        val onClass = ns.owl + "onClass"
        val someValuesFrom = ns.owl + "someValuesFrom"

        val Class = ns.owl + "Class"
        val ObjectProperty = ns.owl + "ObjectProperty"
        val DatatypeProperty = ns.owl + "DatatypeProperty"
        val FunctionalProperty = ns.owl + "FunctionalProperty"
        val InverseFunctionalProperty = ns.owl + "InverseFunctionalProperty"
        val cardinality = ns.owl + "cardinality"
        val NamedIndividual = ns.owl + "NamedIndividual"
        val equivalentClass = ns.owl + "equivalentClass"
        val unionOf = ns.owl + "unionOf"
        val oneOf = ns.owl + "oneOf"
        val inverseOf = ns.owl + "inverseOf"

        val annotatedTarget = ns.owl + "annotatedTarget"
        val annotatedSource = ns.owl + "annotatedSource"

        val maxQualifiedCardinality = ns.owl + "maxQualifiedCardinality"

        val sameAs = ns.owl + "sameAs"
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
        val SequenceElement = ns.java + "SequenceElement"
        val `SequenceElement%3CObject%3E` = ns.java + IRILib.encodeUriComponent("SequenceElement<Object>")
        val UnloadedTypeArray = ns.java + "UnloadedTypeArray"
        val PrimitiveArray = ns.java + "PrimitiveArray"
        val PrimitiveSequenceElement = ns.java + "PrimitiveSequenceElement"
        val hasIndex = ns.java + "hasIndex"
        val hasElement = ns.java + "hasElement"
        val hasSuccessor = ns.java + "hasSuccessor"
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

        val `this` = ns.java + "this"

        val isAtStackDepth = ns.java + "isAtStackDepth"
        val hasJDWPObjectId = ns.java + "hasJDWPObjectId"

        val hasPlainValue = ns.java + "hasPlainValue"

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

        fun genVariableDeclarationURI(variable: LocalVariableInfo): String =
            "${ns.prog}${IRILib.encodeUriComponent(getFullyQualifiedName(variable))}"

        fun genMethodURI(methodInfo: MethodInfo): String =
            "${ns.prog}${IRILib.encodeUriComponent(getFullyQualifiedName(methodInfo.jdiMethod))}"

        fun genReferenceTypeURI(referenceType: ReferenceType): String {
            return "${ns.prog}${IRILib.encodeUriComponent(referenceType.name())}"
        }

        fun genFieldURI(field: Field): String =
            "${ns.prog}${
                IRILib.encodeUriComponent(
                    getFullyQualifiedName(field)
                )
            }"

        fun genUnloadedTypeURI(typeName: String): String {
            return ns.prog + IRILib.encodeUriComponent(typeName)
        }

        fun genLocationURI(locationInfo: LocationInfo): String =
            "${ns.prog}location_${IRILib.encodeUriComponent(locationInfo.id)}"

        fun genTypedHasElementURI(componentTypeName: String): String =
            "${ns.prog}hasElement${IRILib.encodeUriComponent("<${componentTypeName}>")}"

        fun genTypedSequenceElementURI(componentTypeName: String): String =
            "${ns.prog}SequenceElement${IRILib.encodeUriComponent("<${componentTypeName}>")}"

        fun genTypedStoresPrimitiveURI(componentTypeName: String): String =
            "${ns.prog}storesPrimitive${IRILib.encodeUriComponent("<${componentTypeName}>")}"

        fun genTypedStoresReferenceURI(componentTypeName: String): String =
            "${ns.prog}storesReference${IRILib.encodeUriComponent("<${componentTypeName}>")}"
    }

    val prog = ProgURIs()

    inner class RunURIs {
        fun genFrameURI(frameDepth: Int): String =
            "${ns.run}frame$frameDepth"

        private val objectUriPrefix = "${ns.run}object"

        fun genObjectURI(objectReference: ObjectReference): String =
            "$objectUriPrefix${objectReference.uniqueID()}"

        fun isObjectURI(uri: String) =
            uri.startsWith(objectUriPrefix)

        fun genSequenceElementInstanceURI(containerRef: ObjectReference, index: Int) =
            "${ns.run}element${index}_of_${containerRef.uniqueID()}"
    }

    val run = RunURIs()

    inner class LocalURIs {
        val `this` = ns.local + "this"

        fun genLocalVariableURI(variable: LocalVariableInfo): String =
            "${ns.local}${variable.id}"
    }

    val local = LocalURIs()

    inner class MacrosURIs {
        val chainsProperties = ns.macros + "chainsProperties"
    }

    val macros = MacrosURIs()

    /**
    /**
     * Type names may contain characters not allowed in URI fragments or with special meaning, e.g. [] in `java.security.Permission[]`
     *
     * https://en.wikipedia.org/wiki/URI_fragment
     * https://datatracker.ietf.org/doc/html/rfc3986/#section-3.5
     *
     * This method will properly encode them.
    */
     *
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