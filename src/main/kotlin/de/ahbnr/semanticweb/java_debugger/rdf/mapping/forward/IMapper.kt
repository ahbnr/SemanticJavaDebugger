package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward

import org.apache.jena.rdf.model.Model

interface IMapper {
    fun extendModel(buildParameters: BuildParameters, outputModel: Model)
}