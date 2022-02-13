package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.macros

import org.apache.jena.rdf.model.Model

interface Macro {
    fun executeAll(rdfGraph: Model)
}