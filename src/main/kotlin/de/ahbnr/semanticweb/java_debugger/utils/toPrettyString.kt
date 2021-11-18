package de.ahbnr.semanticweb.java_debugger.utils

import org.apache.jena.rdf.model.*
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.util.PrintUtil
import java.lang.StringBuilder

fun Resource.toPrettyString(model: Model): String {
    val prefix = if (this.nameSpace == null) "null" else model.getNsURIPrefix(this.nameSpace)

    return "$prefix:${this.localName}"
}

fun Literal.toPrettyString(model: Model): String = "\"$this\""

fun RDFNode.toPrettyString(model: Model): String {
    val self = this;
    return this.visitWith(object : RDFVisitor {
        override fun visitBlank(blank: Resource, id: AnonId) = "[ ... ]"
        override fun visitLiteral(l: Literal) = l.toPrettyString(model)
        override fun visitURI(r: Resource, uri: String) = r.toPrettyString(model)
    }).toString()
}

fun Statement.toPrettyString(model: Model): String {
    val subjectName = this.subject.toPrettyString(model)
    val predicateName = this.predicate.toPrettyString(model)
    val objectName = this.`object`.toPrettyString(model)

    return "$subjectName $predicateName $objectName ."
}

