@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.VMDisconnectEvent
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream


class JvmInstance(
    val vm: VirtualMachine,
    val eventHandler: IJvmEventHandler
) : Closeable, KoinComponent {
    private val logger: Logger by inject()
    var state: JvmState? = null
        private set

    private val process = vm.process()

    private var isClosed = false
    private fun assertConnected() {
        if (isClosed) {
            throw RuntimeException("Can not operate on closed JVM instance.")
        }
    }

    init {
        redirectStream(process.inputStream, logger.logStream())
        redirectStream(process.errorStream, logger.errorStream())

        // outputCollector = ConcurrentLineCollector(procStdoutStream, procStderrStream)
    }

    private fun redirectStream(inputStream: InputStream, outputStream: OutputStream) {
        val thread = Thread {
            inputStream.transferTo(outputStream)
        }
        thread.priority =
            Thread.MAX_PRIORITY - 1 // needs high priority to display all messages before the debugger exits
        thread.start()
    }

    fun setBreakpointOnReferenceType(referenceType: ReferenceType, line: Int) {
        assertConnected()

        val location = referenceType.locationsOfLine(line).firstOrNull()
        if (location == null) {
            logger.error("Can not set breakpoint: There is no line $line in class ${referenceType.name()}.")
            return
        }

        val bpReq = vm.eventRequestManager().createBreakpointRequest(location)
        bpReq.enable()

        logger.log("Set breakpoint at $location.")
    }

    fun getClass(className: String): ClassType? {
        assertConnected()

        return vm.classesByName(className)?.firstOrNull() as? ClassType
    }

    fun resume() {
        assertConnected()

        state = null
        vm.resume()

        // FIXME: Properly handle class loading: https://dzone.com/articles/monitoring-classloading-jdi

        var eventSet: EventSet? = null
        var paused = false
        while (!paused && vm.eventQueue().remove().also { eventSet = it } != null) {
            assertConnected()

            for (event in eventSet!!) {
                when (event) {
                    is BreakpointEvent -> {
                        state = JvmState(event.thread())
                        paused = true
                    }
                    is VMDisconnectEvent -> {
                        paused = true
                        close()
                    }
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

    override fun close() {
        assertConnected()

        try {
            // We probably shouldnt kill the VM. What if it is an external VM we connected to?
            // vm.resume()
            // vm.exit(-1)
            vm.dispose()
        } catch (e: VMDisconnectedException) {
            // can happen if VM crashed internally, so we can ignore this.
        } finally {
            process.destroy()
        }
    }
}