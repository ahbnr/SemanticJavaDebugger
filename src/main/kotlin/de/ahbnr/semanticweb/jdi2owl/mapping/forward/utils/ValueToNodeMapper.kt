package de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils

import com.sun.jdi.*
import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.OntURIs
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
            is BooleanValue -> NodeFactory.createLiteralByValue(value.value(), XSDDatatype.XSDboolean)
            is ByteValue -> NodeFactory.createLiteralByValue(value.value(), XSDDatatype.XSDbyte)
            is CharValue -> NodeFactory.createLiteralByValue(
                value.value().code,
                XSDDatatype.XSDunsignedShort
            )
            is DoubleValue -> {
                // val plainValue = value.value()

                // val stringRepresentation = when {
                //     plainValue.isInfinite() -> "${
                //         if (plainValue < 0) "-" else ""
                //     }INF"
                //     else -> plainValue.toString()
                // }

                // NodeFactory.createLiteral(stringRepresentation, XSDDatatype.XSDdouble)

                // FIXME: Previously I have been creating the appropriate string representation manually, see above,
                //   and called the createLiteral() method.
                //   Now I am using the "createLiteralByValue" method and am hoping that it is doing the right thing.
                //   I should test this.
                NodeFactory.createLiteralByValue(value.value(), XSDDatatype.XSDdouble)
            }
            is FloatValue -> {
                // val plainValue = value.value()

                // val stringRepresentation = when {
                //     plainValue.isInfinite() -> "${
                //         if (plainValue < 0) "-" else ""
                //     }INF"
                //     else -> plainValue.toString()
                // }

                // NodeFactory.createLiteral(stringRepresentation, XSDDatatype.XSDfloat)

                // FIXME: Previously I have been creating the appropriate string representation manually, see above,
                //   and called the createLiteral() method.
                //   Now I am using the "createLiteralByValue" method and am hoping that it is doing the right thing.
                //   I should test this.
                NodeFactory.createLiteralByValue(value.value(), XSDDatatype.XSDfloat)
            }
            is IntegerValue -> NodeFactory.createLiteralByValue(value.value(), XSDDatatype.XSDint)
            is LongValue -> NodeFactory.createLiteralByValue(value.value(), XSDDatatype.XSDlong)
            is ShortValue -> NodeFactory.createLiteralByValue(value.value(), XSDDatatype.XSDshort)
            else -> {
                logger.error("Encountered unknown kind of primitive value: $value.")
                null
            }
        }
}