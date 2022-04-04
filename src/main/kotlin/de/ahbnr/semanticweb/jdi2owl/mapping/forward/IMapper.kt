package de.ahbnr.semanticweb.jdi2owl.mapping.forward

import org.apache.jena.rdf.model.Model

interface IMapper {
    fun extendModel(buildParameters: BuildParameters, outputModel: Model)
}