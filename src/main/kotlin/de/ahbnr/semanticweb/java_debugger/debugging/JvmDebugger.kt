@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.Bootstrap
import com.sun.jdi.ReferenceType
import com.sun.jdi.event.*
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class JvmDebugger: KoinComponent {
    var jvm: JvmInstance? = null
        private set

    private val deferredBreakpoints = mutableMapOf<String, MutableList<Int>>()

    private val logger: Logger by inject()

    fun setBreakpoint(className: String, line: Int) {
        val classType = jvm?.getClass(className)
        if (classType != null) {
            jvm?.setBreakpointOnReferenceType(classType, line)
        }

        else {
            val lines = deferredBreakpoints.getOrPut(className, { mutableListOf() })
            lines.add(line)

            logger.log("Deferred setting the breakpoint until the class in question is loaded.")
        }
    }

    private fun tryApplyingDeferredBreakpoints(jvm: JvmInstance, preparedType: ReferenceType) {
        val typeName = preparedType.name()
        val lines = deferredBreakpoints.getOrDefault(typeName, null)

        if (lines != null) {
            for (line in lines) {
                jvm.setBreakpointOnReferenceType(preparedType, line)
            }

            deferredBreakpoints.remove(typeName)
        }
    }

    private val eventHandler = object: IJvmEventHandler {
        override fun handleEvent(jvm: JvmInstance, event: Event) {
            when (event) {
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

    fun launchVM(mainClass: String) {
        if (jvm != null) {
            logger.error("There is a VM already running.")
        }

        val launchingConnector = Bootstrap
            .virtualMachineManager()
            .defaultConnector()

        val arguments = launchingConnector.defaultArguments()
        arguments["main"]!!.setValue(mainClass)

        val rawVM = launchingConnector.launch(arguments)

        val classPrepareRequest = rawVM.eventRequestManager().createClassPrepareRequest()
        classPrepareRequest.addClassFilter(mainClass)
        classPrepareRequest.enable()

        jvm = JvmInstance(
            rawVM,
            eventHandler
        )
        jvm?.resume()
    }

    // fun sendClassPrepareRequest(vm: VirtualMachine) {
    //     val classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest()
    //     classPrepareRequest.addClassFilter(classToDebug)
    //     classPrepareRequest.enable()
    // }
}
