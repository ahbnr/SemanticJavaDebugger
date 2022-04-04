package de.ahbnr.semanticweb.jdi2owl.mapping.datatypes

import com.sun.jdi.Accessible
import de.ahbnr.semanticweb.jdi2owl.mapping.OntURIs
import org.apache.jena.datatypes.BaseDatatype
import org.apache.jena.datatypes.DatatypeFormatException
import org.apache.jena.datatypes.TypeMapper
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.impl.LiteralLabel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class JavaAccessModifierDatatype : BaseDatatype {
    companion object : KoinComponent {
        val instance by lazy {
            val instance = JavaAccessModifierDatatype(get<OntURIs>().java.AccessModifier)

            instance
        }

        fun register() {
            TypeMapper.getInstance().registerDatatype(instance)
        }
    }

    private constructor(uri: String) : super(uri) {}

    enum class AccessModifierLiteral(val value: String) {
        `package-private`("package-private"),
        `private`("private"),
        `protected`("protected"),
        `public`("public");

        companion object {
            fun fromJdiAccessible(accessible: Accessible) =
                when {
                    accessible.isPrivate -> `private`
                    accessible.isProtected -> `protected`
                    accessible.isPublic -> `public`
                    else -> `package-private`
                }
        }

        fun toNode() = NodeFactory.createLiteral(this.value)
    }

    private val allowedLiterals = enumValues<AccessModifierLiteral>().map { it.value }.toSet()

    override fun parse(lexicalForm: String): Any {
        if (!allowedLiterals.contains(lexicalForm))
            throw DatatypeFormatException()

        return TypedValue(lexicalForm, getURI())
    }

    override fun isValidLiteral(lit: LiteralLabel): Boolean {
        val literalString = lit.value
        return allowedLiterals.contains(literalString) && lit.datatypeURI == XSDDatatype.XSDstring.uri
    }
}