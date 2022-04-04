package de.ahbnr.semanticweb.jdi2owl.debugging

import com.sun.jdi.*
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.IterableMirror
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils.MirroringError
import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.MappingLimiter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class JvmObjectIterator(
    private val thread: ThreadReference,
    private val limiter: MappingLimiter,
    private val contextRecorder: ReferenceContexts?
) : KoinComponent {
    private val logger: Logger by inject()

    private val hasBeenDeepInspected = mutableSetOf<Long>()
    private val seen = mutableSetOf<Long>()

    private var encounteredAbsentInformationError = false

    /**
     * Utility function to iterate over all objects
     */
    fun iterateObjects(): Sequence<ObjectReference> = sequence {
        // get objects in variables
        for (stackRef in iterateStackReferences()) {
            yieldAll(
                recursivelyIterateObject(stackRef)
            )

            // Objects on the stack are always deeply inspected
            yieldAll(tryDeepIteration(stackRef))
        }

        yieldAll(iterateStaticReferences())
    }

    private fun iterateArray(
        arrayReference: ArrayReference
    ): Sequence<ObjectReference> = sequence {
        for (idx in 0 until arrayReference.length()) {
            val arrayElement = arrayReference.getValue(idx)
            if (arrayElement is ObjectReference) {
                yieldAll(
                    recursivelyIterateObject(arrayElement)
                )
            }
        }
    }

    private fun iterateIterable(
        iterableReference: ObjectReference
    ): Sequence<ObjectReference> = sequence {
        try {
            val iterable = IterableMirror(iterableReference, thread)
            val iterator = iterable.iterator()

            if (iterator != null) {
                for (iterableElement in iterator.asSequence()) {
                    if (iterableElement != null) {
                        yieldAll(
                            recursivelyIterateObject(iterableElement)
                        )
                    }
                }
            } else {
                logger.warning("Could not inspect contents of java.lang.Iterable object ${iterableReference.uniqueID()} because its iterator() method returned null.")
            }
        } catch (e: MirroringError) {
            logger.error(e.message)
        }
    }

    private fun <T> tryGetInfo(task: () -> T): T? =
        try {
            task.invoke()
        } catch (e: AbsentInformationException) {
            encounteredAbsentInformationError = true
            null
        }

    private fun iterateStackReferences(): Sequence<ObjectReference> = sequence {
        for (frameDepth in 0 until thread.frameCount()) {
            // the frame reference must be freshly retrieved every time,
            // since any resuming of the thread  (e.g. due to a invokeMethod call)
            // can invalidate frame references.

            fun frame() = thread.frame(frameDepth)
            val method = frame().location().method()

            val thisRef = frame().thisObject()
            if (thisRef != null) {
                yield(thisRef)
            }

            val stackReferences = frame()
                .getValues(
                    tryGetInfo {
                        frame().visibleVariables()
                    }
                        ?: emptyList()
                )

            yieldAll(
                if (contextRecorder != null)
                    stackReferences
                        .asSequence()
                        .mapNotNull { (variable, value) ->
                            if (value is ObjectReference) {
                                contextRecorder.addContext(
                                    value,
                                    ReferenceContexts.Context.ReferencedByStack(method, variable)
                                )
                                value
                            } else null
                        }
                else stackReferences
                    .values
                    .asSequence()
                    .filterIsInstance<ObjectReference>()
            )
        }
    }

    private fun iterateStaticReferences(): Sequence<ObjectReference> = sequence {
        val vm = thread.virtualMachine()
        val allReferenceTypes = vm.allClasses()

        for (referenceType in allReferenceTypes) {
            if (limiter.canReferenceTypeBeSkipped(referenceType))
                continue

            if (!referenceType.isPrepared)
                continue // skip details if this class has not been fully prepared in the VM state

            for (field in referenceType.allFields()) {
                if (!field.isStatic)
                    continue

                if (limiter.canFieldBeSkipped(field))
                    continue

                val value = referenceType.getValue(field)
                if (value !is ObjectReference)
                    continue

                contextRecorder?.addContext(value, ReferenceContexts.Context.ReferencedByField(field))

                yieldAll(
                    recursivelyIterateObject(value)
                )

                // Now that we have a field context, maybe the object is eligible for deep iteration
                yieldAll(tryDeepIteration(value))
            }

            // Reference types themselves are also objects
            // FIXME: No recording of context for these?
            yieldAll(
                recursivelyIterateObject(
                    referenceType.classObject()
                )
            )

            // FIXME: Arent modules also objects?
        }
    }

    private fun tryDeepIteration(value: ObjectReference) = sequence {
        val id = value.uniqueID()
        if (hasBeenDeepInspected.contains(id))
            return@sequence

        if (limiter.canSequenceBeSkipped(value, contextRecorder))
            return@sequence

        hasBeenDeepInspected.add(id)

        if (value is ArrayReference) {
            yieldAll(iterateArray(value))
        } else {
            val referenceType = value.referenceType()
            if (referenceType is ClassType) {
                if (referenceType.allInterfaces().any { it.name() == "java.lang.Iterable" }) {
                    yieldAll(iterateIterable(value))
                }
            }
        }
    }

    private fun recursivelyIterateObject(
        objectReference: ObjectReference
    ): Sequence<ObjectReference> = sequence {
        val id = objectReference.uniqueID()
        if (seen.contains(id))
            return@sequence
        seen.add(id)

        val referenceType = objectReference.referenceType()

        if (limiter.canReferenceTypeBeSkipped(referenceType))
            return@sequence

        for (field in referenceType.allFields()) {
            if (field.isStatic)
                continue

            if (limiter.canFieldBeSkipped(field))
                continue

            val value = objectReference.getValue(field)

            if (value !is ObjectReference)
                continue

            contextRecorder?.addContext(value, ReferenceContexts.Context.ReferencedByField(field))
            yieldAll(recursivelyIterateObject(value))

            // Now that we have a field context, maybe the object is eligible for deep iteration
            yieldAll(tryDeepIteration(value))
        }

        yield(objectReference)
    }

    fun reportErrors() {
        if (encounteredAbsentInformationError) {
            logger.error("Could not extract all necessary information through JDI!")
            logger.emphasize("Did you forget to compile your program with debug information (-g flag)?")
        }
    }
}