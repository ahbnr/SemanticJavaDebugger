@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.jdi2owl.debugging

import com.sun.jdi.Bootstrap
import com.sun.jdi.ReferenceType
import com.sun.jdi.event.*
import com.sun.jdi.request.BreakpointRequest
import de.ahbnr.semanticweb.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.Closeable


class JvmDebugger : Closeable, KoinComponent {
    var jvm: JvmInstance? = null
        private set

    private data class Breakpoint(val line: Int, val callback: (() -> Boolean)?)

    private val breakpoints = mutableMapOf<String, MutableSet<Breakpoint>>()
    private val deferredBreakpoints = mutableMapOf<String, MutableSet<Breakpoint>>()

    private val logger: Logger by inject()

    fun setBreakpoint(
        className: String,
        line: Int,
        callback: (() -> Boolean)?
    ) {
        val newBreakpoint = Breakpoint(line, callback)

        val breakpointsInFile = breakpoints
            .getOrPut(className) { mutableSetOf() }

        if (breakpointsInFile.any { it.line == newBreakpoint.line }) {
            logger.debug("There is already a breakpoint at line $line.")
            return
        }

        breakpointsInFile.add(newBreakpoint)

        val classType = jvm?.getClass(className)
        if (classType != null) {
            jvm?.setBreakpointOnReferenceType(classType, line)
        } else {
            val deferredBreakpointsInFile = deferredBreakpoints.getOrPut(className) { mutableSetOf() }
            deferredBreakpointsInFile.add(newBreakpoint)

            val rawVM = jvm?.vm
            if (rawVM != null) {
                val prepareReq = rawVM.eventRequestManager().createClassPrepareRequest()
                prepareReq.addClassFilter(className)
                prepareReq.enable()
            }

            logger.log("Deferred setting the breakpoint until the class in question is loaded.")
        }
    }

    private fun tryApplyingDeferredBreakpoints(jvm: JvmInstance, preparedType: ReferenceType) {
        val className = preparedType.name()
        val breakpoints = deferredBreakpoints.getOrDefault(className, null)

        if (breakpoints != null) {
            for (breakpoint in breakpoints) {
                jvm.setBreakpointOnReferenceType(preparedType, breakpoint.line)
            }

            deferredBreakpoints.remove(className)
        }
    }

    private val eventHandler = object : IJvmEventHandler {
        override fun handleEvent(jvm: JvmInstance, event: Event): HandleEventResult =
            when (event) {
                is VMStartEvent -> {
                    logger.log("JVM started.")
                    HandleEventResult.Nothing
                }

                is ClassPrepareEvent -> {
                    tryApplyingDeferredBreakpoints(jvm, event.referenceType())
                    HandleEventResult.Nothing
                }

                is BreakpointEvent -> {
                    val request = event.request() as? BreakpointRequest
                    val location = request?.location()
                    val className = location?.declaringType()?.name()
                    val breakpoint = if (className != null)
                        breakpoints
                            .getOrElse(className, { setOf() })
                            .find { it.line == location.lineNumber() }
                    else null

                    if (breakpoint == null) {
                        logger.error("Hit a breakpoint which was not specified by this debugger: $event. This should never happen.")
                        HandleEventResult.Nothing
                    } else {
                        if (breakpoint.callback != null) {
                            if (!breakpoint.callback.invoke())
                                HandleEventResult.ForceResume
                            else HandleEventResult.Nothing
                        } else {
                            logger.log("Breakpoint hit: $event")
                            HandleEventResult.Nothing
                        }
                    }
                }

                is VMDisconnectEvent -> {
                    logger.log("The JVM terminated.")
                    this@JvmDebugger.jvm = null

                    HandleEventResult.Nothing
                }

                else -> HandleEventResult.Nothing
            }
    }

    fun launchVM(mainClassAndArgs: String, classpaths: List<String>) {
        if (jvm != null) {
            logger.debug("There is a JVM already running.")
            logger.emphasize("Closing existing JVM and creating new one...")
            close()
        }

        deferredBreakpoints.clear()
        deferredBreakpoints.putAll(breakpoints)

        val launchingConnector = Bootstrap
            .virtualMachineManager()
            .defaultConnector()

        val arguments = launchingConnector.defaultArguments()
        arguments["main"]!!.setValue(mainClassAndArgs)

        arguments["options"]!!.setValue(classpaths.joinToString(" ") { "-cp $it" })

        val rawVM = launchingConnector.launch(arguments)

        for (breakpointClass in deferredBreakpoints.keys) {
            val req = rawVM.eventRequestManager().createClassPrepareRequest()
            req.addClassFilter(breakpointClass)
            req.enable()
        }

        jvm = JvmInstance(
            rawVM,
            eventHandler
        )
    }

    fun kill() {
        jvm?.close()
        jvm = null
    }

    override fun close() {
        kill()
    }
}
