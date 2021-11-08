import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.VMDisconnectEvent
import de.ahbnr.semanticweb.java_debugger.debugging.Debugger
import de.ahbnr.semanticweb.java_debugger.utils.ConcurrentLineCollector


fun main(args: Array<String>) {
    println(args[0])
    val debuggerInstance = Debugger(args[0])

    try {
        val vm = debuggerInstance.launchVM()
        val process = vm.process()

        val procStdoutStream = process.inputStream
        val procStderrStream = process.errorStream

        val outputCollector = ConcurrentLineCollector(procStdoutStream, procStderrStream)

        // Start the VM (necessary!)
        vm.resume()

        debuggerInstance.sendClassPrepareRequest(vm)
        var eventSet: EventSet? = null
        var connected = true
        while (connected && vm.eventQueue().remove().also { eventSet = it } != null) {
            for (event in eventSet!!) {
                if (event is ClassPrepareEvent) {
                    debuggerInstance.setBreakpoint(vm, event, 4)
                }
                if (event is BreakpointEvent) {
                    debuggerInstance.displayVariables(event)
                }
                if (event is VMDisconnectEvent) {
                    connected = false
                }

                vm.resume()
            }
        }

        for (line in outputCollector.seq) {
            println(line)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}