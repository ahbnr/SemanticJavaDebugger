package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ValueToNodeMapper : KoinComponent {
    private val URIs: OntURIs by inject()
    private val logger: Logger by inject()

    fun map(value: Value?): Node? =
        when (value) {
            is PrimitiveValue -> mapPrimitiveValue(value)
            is ObjectReference -> NodeFactory.createURI(URIs.run.genObjectURI(value))
            // apparently null values are mirrored directly as null:
            // https://docs.oracle.com/en/java/javase/11/docs/api/jdk.jdi/com/sun/jdi/Value.html
            null -> NodeFactory.createURI(URIs.java.`null`)
            else -> {
                logger.error("Encountered unknown kind of value: ${value}.")
                null
            }
        }

    private fun mapPrimitiveValue(value: PrimitiveValue): Node? =
        when (value) {
            is BooleanValue -> NodeFactory.createLiteral(value.value().toString(), XSDDatatype.XSDboolean)
            is ByteValue -> NodeFactory.createLiteral(value.value().toString(), XSDDatatype.XSDbyte)
            is CharValue -> NodeFactory.createLiteral(
                value.value().code.toString(),
                XSDDatatype.XSDunsignedShort
            )
            is DoubleValue -> NodeFactory.createLiteral(value.value().toString(), XSDDatatype.XSDdouble)
            is FloatValue -> NodeFactory.createLiteral(value.value().toString(), XSDDatatype.XSDfloat)
            is IntegerValue -> NodeFactory.createLiteral(value.value().toString(), XSDDatatype.XSDint)
            is LongValue -> NodeFactory.createLiteral(value.value().toString(), XSDDatatype.XSDlong)
            is ShortValue -> NodeFactory.createLiteral(value.value().toString(), XSDDatatype.XSDshort)
            else -> {
                logger.error("Encountered unknown kind of primitive value: $value.")
                null
            }
        }
}