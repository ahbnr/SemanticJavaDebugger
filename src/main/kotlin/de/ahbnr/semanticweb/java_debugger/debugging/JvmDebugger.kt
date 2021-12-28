@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.Bootstrap
import com.sun.jdi.ReferenceType
import com.sun.jdi.event.*
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.Closeable


class JvmDebugger : Closeable, KoinComponent {
    var jvm: JvmInstance? = null
        private set

    private val breakpoints = mutableMapOf<String, MutableSet<Int>>()
    private val deferredBreakpoints = mutableMapOf<String, MutableSet<Int>>()

    private val logger: Logger by inject()

    fun setBreakpoint(className: String, line: Int) {
        val lines = breakpoints.getOrPut(className) { mutableSetOf() }
        lines.add(line)

        val classType = jvm?.getClass(className)
        if (classType != null) {
            jvm?.setBreakpointOnReferenceType(classType, line)
        } else {
            val lines = deferredBreakpoints.getOrPut(className) { mutableSetOf() }
            lines.add(line)

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
        val lines = deferredBreakpoints.getOrDefault(className, null)

        if (lines != null) {
            for (line in lines) {
                jvm.setBreakpointOnReferenceType(preparedType, line)
            }

            deferredBreakpoints.remove(className)
        }
    }

    private val eventHandler = object : IJvmEventHandler {
        override fun handleEvent(jvm: JvmInstance, event: Event) {
            when (event) {
                is VMStartEvent -> {
                    logger.log("JVM started.")
                }

                is ClassPrepareEvent -> {
                    tryApplyingDeferredBreakpoints(jvm, event.referenceType())
                }

                is BreakpointEvent -> {
                    logger.log("Breakpoint hit: $event")
                }

                is VMDisconnectEvent -> {
                    logger.log("The JVM terminated.")
                    this@JvmDebugger.jvm = null
                }
            }
        }
    }

    fun launchVM(mainClass: String, classpath: String? = null) {
        if (jvm != null) {
            logger.debug("There is a JVM already running.")
            logger.emphasize("Closing existing JVM and creating new one...")
            close()

            deferredBreakpoints.clear()
            deferredBreakpoints.putAll(breakpoints)
        }

        val launchingConnector = Bootstrap
            .virtualMachineManager()
            .defaultConnector()

        val arguments = launchingConnector.defaultArguments()
        arguments["main"]!!.setValue(mainClass)
        if (classpath != null) {
            arguments["options"]!!.setValue("-cp $classpath")
        }

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

    override fun close() {
        jvm?.close()
        jvm = null
    }
}
