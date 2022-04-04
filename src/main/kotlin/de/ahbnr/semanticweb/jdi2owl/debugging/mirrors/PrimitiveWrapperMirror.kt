package de.ahbnr.semanticweb.jdi2owl.debugging.mirrors

import com.sun.jdi.*
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils.MirroringError
import de.ahbnr.semanticweb.jdi2owl.debugging.mirrors.utils.retrieveMethod

// Be aware, that invoking any method of this mirror invalidates any frame references for the thread
// and that they have to be retrieved again via frame(i)
open class PrimitiveWrapperMirror<
        ValueMirrorType : Value,
        WrappedJavaType
        >(
    private val klass: Class<ValueMirrorType>,
    private val wrapperObjectRef: ObjectReference,
    private val thread: ThreadReference,
    private val valueMethodName: String,
    private val extractPrimitiveType: (v: ValueMirrorType) -> WrappedJavaType
) {
    private val valueMethod: Method

    init {
        val classType = wrapperObjectRef.type()
        if (classType !is ClassType) {
            throw MirroringError("Given primitive wrapper object is not of a class type.")
        }

        valueMethod = retrieveMethod(classType, valueMethodName)
    }

    @Suppress("UNCHECKED_CAST")
    fun valueMirror(): ValueMirrorType {
        val result = wrapperObjectRef.invokeMethod(
            thread,
            valueMethod,
            emptyList(),
            0
        )

        if (!klass.isAssignableFrom(result.javaClass)) {
            throw MirroringError("Encountered wrapper object whose $valueMethodName() method did not yield a ${klass.name}. This should never happen.")
        }

        return result as ValueMirrorType
    }

    fun value(): WrappedJavaType {
        return extractPrimitiveType.invoke(valueMirror())
    }
}

class ByteWrapperMirror(
    wrapperObjectRef: ObjectReference,
    thread: ThreadReference,
) : PrimitiveWrapperMirror<ByteValue, Byte>(
    ByteValue::class.java,
    wrapperObjectRef,
    thread,
    "byteValue",
    { it.value() }
)

class ShortWrapperMirror(
    wrapperObjectRef: ObjectReference,
    thread: ThreadReference,
) : PrimitiveWrapperMirror<ShortValue, Short>(
    ShortValue::class.java,
    wrapperObjectRef,
    thread,
    "shortValue",
    { it.value() }
)

class IntegerWrapperMirror(
    wrapperObjectRef: ObjectReference,
    thread: ThreadReference,
) : PrimitiveWrapperMirror<IntegerValue, Int>(
    IntegerValue::class.java,
    wrapperObjectRef,
    thread,
    "intValue",
    { it.value() }
)

class LongWrapperMirror(
    wrapperObjectRef: ObjectReference,
    thread: ThreadReference,
) : PrimitiveWrapperMirror<LongValue, Long>(
    LongValue::class.java,
    wrapperObjectRef,
    thread,
    "longValue",
    { it.value() }
)

class FloatWrapperMirror(
    wrapperObjectRef: ObjectReference,
    thread: ThreadReference,
) : PrimitiveWrapperMirror<FloatValue, Float>(
    FloatValue::class.java,
    wrapperObjectRef,
    thread,
    "floatValue",
    { it.value() }
)

class DoubleWrapperMirror(
    wrapperObjectRef: ObjectReference,
    thread: ThreadReference,
) : PrimitiveWrapperMirror<DoubleValue, Double>(
    DoubleValue::class.java,
    wrapperObjectRef,
    thread,
    "doubleValue",
    { it.value() }
)

class CharacterWrapperMirror(
    wrapperObjectRef: ObjectReference,
    thread: ThreadReference,
) : PrimitiveWrapperMirror<CharValue, Char>(
    CharValue::class.java,
    wrapperObjectRef,
    thread,
    "charValue",
    { it.value() }
)

class BooleanWrapperMirror(
    wrapperObjectRef: ObjectReference,
    thread: ThreadReference,
) : PrimitiveWrapperMirror<BooleanValue, Boolean>(
    BooleanValue::class.java,
    wrapperObjectRef,
    thread,
    "booleanValue",
    { it.value() }
)
