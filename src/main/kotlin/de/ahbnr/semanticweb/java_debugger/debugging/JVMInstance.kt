@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.VMDisconnectEvent
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.utils.ConcurrentLineCollector
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

class JVMInstance(
    val vm: VirtualMachine,
    val eventHandler: JVMEventHandler
): KoinComponent {
    val logger: Logger by inject()
    var state: JVMState? = null
        private set

    init {
        val process = vm.process()

        redirectStream(process.inputStream, logger.logStream())
        redirectStream(process.errorStream, logger.errorStream())

        // outputCollector = ConcurrentLineCollector(procStdoutStream, procStderrStream)
    }

    private fun redirectStream(inputStream: InputStream, outputStream: OutputStream) {
        val thread = Thread {
            inputStream.transferTo(outputStream)
        }
        thread.priority = Thread.MAX_PRIORITY - 1 // needs high priority to display all messages before the debugger exits
        thread.start()
    }

    fun setBreakpointOnReferenceType(referenceType: ReferenceType, line: Int) {
        val location = referenceType.locationsOfLine(line).firstOrNull()
        if (location == null) {
            logger.error("Can not set breakpoint: There is no line ${line} in class ${referenceType.name()}.")
            return
        }

        val bpReq = vm.eventRequestManager().createBreakpointRequest(location)
        bpReq.enable()

        logger.log("Set breakpoint at $location.")
    }

    fun getClass(className: String): ClassType? =
        vm.classesByName(className)?.firstOrNull() as? ClassType

    fun resume() {
        state = null
        vm.resume()

        // FIXME: Properly handle class loading: https://dzone.com/articles/monitoring-classloading-jdi

        var eventSet: EventSet? = null
        var paused = false
        while (!paused && vm.eventQueue().remove().also { eventSet = it } != null) {
            for (event in eventSet!!) {
                when (event) {
                    is BreakpointEvent -> {
                        state = JVMState(event.thread())
                        paused = true
                    }
                    is VMDisconnectEvent -> paused = true
                }

                eventHandler.handleEvent(this, event)

                if (!paused) {
                    vm.resume()
                }
            }
        }

        // FIXME: This blocks!
        // for (line in outputCollector.seq) {
        //     logger.log(line)
        // }
    }
}