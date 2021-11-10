package de.ahbnr.semanticweb.java_debugger.rdf.mapping.mappers

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.*
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.utils.addStatement
import org.apache.jena.rdf.model.Model

class ObjectMapper(
    private val ns: Namespaces
): IMapper {
    private fun addField(field: Field, value: Value?, objectSubject: String, objectReference: ObjectReference, classType: ClassType, outputModel: Model) {
        // we model a field as an instance of the field property of the class.
        // That one is created by the ClassMapper

        // lets find out the object name, i.e. the name of the field value in case of a reference type value,
        // or the value itself, in case of a primitive value
        val valueObject = when(value) {
            null -> ns.java + "null"
            is ObjectReference -> genObjectIndividualName(value)
            else -> null // FIXME: Handle other cases
        }

        if (valueObject != null) {
            val fieldPropertyName = ClassMapper.genFieldPropertyURI(classType, field, ns)
            addStatement(outputModel,
                objectSubject,
                fieldPropertyName,
                valueObject
            )
        }

        // FIXME: Output a warning in the else case
    }

    private fun addFields(objectSubject: String, objectReference: ObjectReference, classType: ClassType, outputModel: Model) {
        // FIXME: Extend this also to the values from superclasses (allFields())
        val fieldValues = objectReference.getValues(classType.fields())

        for ((field, value) in fieldValues) {
            addField(field, value, objectSubject, objectReference, classType, outputModel)
        }
    }

    private fun addObjectNode(objectReference: ObjectReference, classType: ClassType, outputModel: Model) {
        val objectSubject = genObjectIndividualName(objectReference)

        // The object is a particular individual (not a class/concept)
        addStatement(outputModel,
            objectSubject,
            ns.rdf + "type",
            ns.owl + "NamedIndividual"
        )

        // it is a java object
        addStatement(outputModel,
            objectSubject,
            ns.rdf + "type",
            ns.java + "Object"
        )

        // it is of a particular java class
        addStatement(outputModel,
            objectSubject,
            ns.rdf + "type",
            ns.prog + classType.name() // FIXME: we model Java classes as owl classes here, instead of being individuals. Not sure what the right design is here
            // FIXME: Factor out java RDF classname generation
        )

        addFields(objectSubject, objectReference, classType, outputModel)
    }

    override fun extendModel(vm: VirtualMachine, thread: ThreadReference, outputModel: Model) {
        // FIXME: Handle more than current stack frame
        // FIXME: Handle multiple threads?
        val frame = thread.frame(0)
        val variables = frame.visibleVariables()

        if (variables != null) {
            val values = frame.getValues(variables)

            for ((variable, value) in values) {
                println("Checking " + variable.name())

                when (value) {
                    is ObjectReference -> {
                        val referenceType = value.referenceType()

                        when (referenceType) {
                            is ClassType -> {
                                addObjectNode(value, referenceType, outputModel)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun genObjectIndividualName(objectReference: ObjectReference): String =
        // FIXME: The documentation is not really clear on this, but experimentally this should be a unique id for the object being referenced (e.g. two references to the same object return the same id here)
        "${ns.run}object_${objectReference.uniqueID()}"
}