package de.ahbnr.semanticweb.java_debugger.rdf.mapping.mappers

import com.sun.jdi.ClassType
import com.sun.jdi.ThreadReference
import com.sun.jdi.VirtualMachine
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.*
import org.apache.jena.rdf.model.Model

class ClassMapper(): IMapper {
    private fun addStatement(outputModel: Model, subject: String, predicate: String, obj: String) {
        val subjectNode = outputModel.createResource(subject)
        val predicateNode = outputModel.createProperty(predicate)
        val objNode = outputModel.createResource(obj)

        val statement = outputModel.createStatement(subjectNode, predicateNode, objNode)

        outputModel.add(statement)
    }

    private fun addClassNode(vm: VirtualMachine, thread: ThreadReference, classType: ClassType, outputModel: Model) {
        val classSubject = PROG_NAMESPACE + classType.name()

        // classSubject is a java class
        addStatement(outputModel,
            classSubject,
            RDF_NAMESPACE + "type",
            JAVA_NAMESPACE + "Class"
        )

        // classType is an owl class
        addStatement(outputModel,
            classSubject,
            RDF_NAMESPACE + "type",
            OWL_NAMESPACE + "Class"
        )

        // every class is also an object
        addStatement(outputModel,
            classSubject,
            RDFS_NAMESPACE + "subClassOf",
            PROG_NAMESPACE + "Object"
        )
    }

    override fun extendModel(vm: VirtualMachine, thread: ThreadReference, outputModel: Model) {
        val allReferenceTypes = vm.allClasses()

        for (referenceType in allReferenceTypes) {
            when (referenceType) {
                is ClassType -> {
                    addClassNode(vm, thread, referenceType, outputModel)
                }
            }
        }
    }
}