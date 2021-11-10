package de.ahbnr.semanticweb.java_debugger.rdf.mapping.utils

import org.apache.jena.graph.Triple
import org.apache.jena.graph.impl.GraphBase
import org.apache.jena.rdf.model.Model
import org.apache.jena.util.iterator.ExtendedIterator

fun addStatement(outputModel: Model, subject: String, predicate: String, obj: String) {
    //val subjectNode = outputModel.createResource(subject)
    //val predicateNode = outputModel.createProperty(predicate)
    //val objNode = outputModel.createResource(obj)

    //val statement = outputModel.createStatement(subjectNode, predicateNode, objNode)

    //outputModel.add(statement)

    val subjectNode = outputModel.createResource(subject)
    val predicateNode = outputModel.createProperty(predicate)
    val objectNode = outputModel.createResource(obj)
    subjectNode.addProperty(
        predicateNode, objectNode
    )
}
