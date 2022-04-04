package de.ahbnr.semanticweb.jdi2owl.mapping.forward.macros

import org.apache.jena.rdf.model.Model

interface Macro {
    fun executeAll(rdfGraph: Model)
}