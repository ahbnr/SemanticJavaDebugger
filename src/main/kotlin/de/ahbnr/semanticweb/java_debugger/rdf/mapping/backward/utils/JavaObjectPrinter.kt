package de.ahbnr.semanticweb.java_debugger.rdf.mapping.backward.utils

import com.sun.jdi.*
import de.ahbnr.semanticweb.java_debugger.debugging.JvmState
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class JavaObjectPrinter(
    private val jvmState: JvmState
) : KoinComponent {
    private val logger: Logger by inject()

    private val toStringMethod: Method?

    init {
        val vm = jvmState.pausedThread.virtualMachine()
        // Need to obtain the toString method from java.lang.Object
        // because array reference types do not return any methods
        toStringMethod =
            vm.allClasses()
                .find { it.name() == "java.lang.Object" }
                ?.methodsByName("toString")
                ?.firstOrNull()
        if (toStringMethod == null) {
            logger.error("Can not obtain toString() method reference. This should never happen.")
        }
    }

    fun print(ref: ObjectReference) {
        logger.log("Java Object: $ref")

        for ((field, value) in ref.getValues(ref.referenceType().allFields())) {
            if (!field.isStatic) {
                logger.log("  ${field.name()} = $value")
            }
        }

        if (ref is ArrayReference) {
            val values = ref
                .values
                .joinToString(", ") { it?.toString() ?: "null" }

            logger.log("")
            logger.log("  Array contents: [${values}]")

            val doesArrayStoreReferences =
                (ref.referenceType() as? ArrayType)?.componentType() is ReferenceType
            if (doesArrayStoreReferences) {
                val strings = ref
                    .values
                    .joinToString(", ") {
                        if (it != null) {
                            (it as ObjectReference).invokeMethod(
                                jvmState.pausedThread,
                                toStringMethod,
                                emptyList(),
                                0
                            ).toString()
                        } else "null"
                    }
                logger.log("  As strings: [${strings}]")
            }
        } else if (toStringMethod != null) {
            // Be aware, that this invalidates any frame references for the paused thread
            // and that they have to be retrieved again via frame(i)
            val stringRepresentation = ref.invokeMethod(
                jvmState.pausedThread,
                toStringMethod,
                emptyList(),
                0
            )

            logger.log("")
            logger.log("  toString(): $stringRepresentation")
        }
    }
}