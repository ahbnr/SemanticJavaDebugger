package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import com.sun.jdi.*
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory

fun mapPrimitiveValue(value: PrimitiveValue): Node? =
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
        else -> null
    }