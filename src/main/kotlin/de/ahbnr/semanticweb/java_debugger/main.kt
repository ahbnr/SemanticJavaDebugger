import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.VMDisconnectEvent
import de.ahbnr.semanticweb.java_debugger.debugging.Debugger
import de.ahbnr.semanticweb.java_debugger.utils.ConcurrentLineCollector

class SemanticJavaDebugger: CliktCommand() {
    val classToDebug by argument()
    val lineNum: Int by argument().int()
    val applicationDomainDefinition: String? by option()

    override fun run() {
        val debuggerInstance = Debugger(classToDebug, lineNum)

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
                    // FIXME: Deal with this: https://dzone.com/articles/monitoring-classloading-jdi
                    if (event is ClassPrepareEvent) {
                        debuggerInstance.setBreakpoint(vm, event, lineNum)
                    }
                    if (event is BreakpointEvent) {
                        debuggerInstance.displayVariables(applicationDomainDefinition, event)
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
}

fun main(args: Array<String>) {
    SemanticJavaDebugger().main(args)
}