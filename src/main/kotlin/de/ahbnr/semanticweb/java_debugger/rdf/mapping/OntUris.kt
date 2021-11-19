package de.ahbnr.semanticweb.java_debugger.rdf.mapping

import com.sun.jdi.*
import org.apache.jena.atlas.lib.IRILib

class OntURIs(val ns: Namespaces) {
    inner class RdfURIs {
        val type = "type"
    }

    val rdf = RdfURIs()

    inner class RdfsURIs {
        val subClassOf = ns.rdfs + "subClassOf"
        val domain = ns.rdfs + "domain"
        val range = ns.rdfs + "range"
    }

    val rdfs = RdfsURIs()

    inner class OwlURIs {
        val Class = ns.owl + "Class"
        val ObjectProperty = ns.owl + "ObjectProperty"
        val FunctionalProperty = ns.owl + "FunctionalProperty"
        val NamedIndividual = ns.owl + "NamedIndividual"
    }

    val owl = OwlURIs()

    inner class JavaURIs {
        val UnloadedType = ns.java + "UnloadedType"
        val Class = ns.java + "Class"
        val Method = ns.java + "Method"
        val Field = ns.java + "Field"
        val VariableDeclaration = ns.java + "VariableDeclaration"

        val long = ns.java + "long"

        val hasMethod = ns.java + "hasMethod"
        val hasField = ns.java + "hasField"
        val declaresVariable = ns.java + "declaresVariable"

        val `null` = ns.java + "null"

        val Object = ns.java + "Object"
        val StackFrame = ns.java + "StackFrame"
        val LocalVariable = ns.java + "LocalVariable"

        val isAtStackDepth = ns.java + "isAtStackDepth"
        val hasLocalVariable = ns.java + "hasLocalVariable"
        val declaredByVariableDeclaration = ns.java + "declaredByVariableDeclaration"
        val storesReferenceTo = ns.java + "storesReferenceTo"
        val hasJDWPObjectId = ns.java + "hasJDWPObjectId"
    }

    val java = JavaURIs()

    inner class ProgURIs {
        val Object = ns.prog + "Object"

        fun genVariableDeclarationURI(variable: LocalVariable, method: Method, referenceType: ReferenceType): String =
            "${ns.prog}${IRILib.encodeUriComponent(referenceType.name())}_${IRILib.encodeUriComponent(method.name())}_${
                IRILib.encodeUriComponent(
                    variable.name()
                )
            }"

        fun genMethodURI(method: Method, classType: ClassType): String =
            "${ns.prog}${IRILib.encodeUriComponent(classType.name())}_${IRILib.encodeUriComponent(method.name())}"

        fun genReferenceTypeURI(referenceType: ReferenceType): String {
            return "${ns.prog}${IRILib.encodeUriComponent(referenceType.name())}"
        }

        fun genFieldURI(classType: ClassType, field: Field): String =
            "${ns.prog}${IRILib.encodeUriComponent(classType.name())}_${IRILib.encodeUriComponent(field.name())}"

        fun genUnloadedTypeURI(typeName: String): String {
            return ns.prog + IRILib.encodeUriComponent(typeName)
        }
    }

    val prog = ProgURIs()

    inner class RunURIs {
        fun genFrameURI(frameDepth: Int): String =
            "${ns.run}frame$frameDepth"

        fun genLocalVariableURI(frameDepth: Int, variable: LocalVariable): String =
            "${ns.run}frame${frameDepth}_${IRILib.encodeUriComponent(variable.name())}"

        fun genObjectURI(objectReference: ObjectReference): String =
            "${ns.run}object_${objectReference.uniqueID()}"
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