package de.ahbnr.semanticweb.java_debugger.rdf.mapping

import com.sun.jdi.ThreadReference
import com.sun.jdi.VirtualMachine
import org.apache.jena.rdf.model.Model

interface IMapper {
    fun extendModel(vm: VirtualMachine, thread: ThreadReference, outputModel: Model)
}