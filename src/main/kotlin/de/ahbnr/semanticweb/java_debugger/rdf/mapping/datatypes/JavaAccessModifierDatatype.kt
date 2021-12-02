package de.ahbnr.semanticweb.java_debugger.rdf.mapping.datatypes

import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import org.apache.jena.datatypes.BaseDatatype
import org.apache.jena.datatypes.DatatypeFormatException
import org.apache.jena.datatypes.TypeMapper
import org.apache.jena.graph.impl.LiteralLabel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class JavaAccessModifierDatatype : BaseDatatype {
    companion object : KoinComponent {
        val instance by lazy {
            val instance = JavaAccessModifierDatatype(get<OntURIs>().java.AccessModifier)
            TypeMapper.getInstance().registerDatatype(instance)

            instance
        }
    }

    private constructor(uri: String) : super(uri) {}

    enum class AccessModifierLiteral(val value: String) {
        `package-private`("package-private"),
        `private`("private"),
        `protected`("protected"),
        `public`("public"),
    }

    private val allowedLiterals = enumValues<AccessModifierLiteral>().map { it.value }.toSet()

    override fun parse(lexicalForm: String): Any {
        if (!allowedLiterals.contains(lexicalForm))
            throw DatatypeFormatException()

        return TypedValue(lexicalForm, getURI())
    }

    override fun isValidLiteral(lit: LiteralLabel): Boolean {
        val literalString = (lit.value as TypedValue).lexicalValue
        return allowedLiterals.contains(literalString) && lit.datatypeURI == getURI()
    }
}