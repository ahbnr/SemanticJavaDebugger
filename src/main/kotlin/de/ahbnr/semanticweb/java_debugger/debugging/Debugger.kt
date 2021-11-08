package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.Bootstrap
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.LocatableEvent
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.mappers.ClassMapper
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.ResultSetFormatter

class Debugger(private val classToDebug: String) {
    fun launchVM(): VirtualMachine {
        val launchingConnector = Bootstrap.virtualMachineManager()
            .defaultConnector()

        val arguments = launchingConnector.defaultArguments()
        arguments["main"]!!.setValue(classToDebug)

        return launchingConnector.launch(arguments)
    }

    fun sendClassPrepareRequest(vm: VirtualMachine) {
        val classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest()
        classPrepareRequest.addClassFilter(classToDebug)
        classPrepareRequest.enable()
    }

    fun setBreakpoint(vm: VirtualMachine, classPrepareEvent: ClassPrepareEvent, line: Int) {
        val classType = classPrepareEvent.referenceType()

        val location = classType.locationsOfLine(line).get(0)

        val bpReq = vm.eventRequestManager().createBreakpointRequest(location)

        bpReq.enable()
    }

    fun displayVariables(locatableEvent: LocatableEvent) {
        val stackFrame = locatableEvent.thread().frame(0)

        if (stackFrame.location().toString().contains(classToDebug)) {
            val visibleVariables = stackFrame.getValues(stackFrame.visibleVariables())

            println("Variables at " + stackFrame.location().toString().toString() + " > ")
            for ((key, value) in visibleVariables) {
                println(key.name().toString() + " = " + value)
            }

            println("RDF Graph")
            val graphGen = GraphGenerator(listOf(ClassMapper()))
            val model = graphGen.getGraphModel(locatableEvent.virtualMachine(), locatableEvent.thread())
            //model.write(System.out)

            val query = QueryFactory.create("""
                SELECT ?p ?o
                WHERE { <https://github.com/ahbnr/SemanticJavaDebugger/Program#HelloWorld> ?p  ?o }
            """.trimIndent())

            val queryExecution = QueryExecutionFactory.create(query, model)

            val results = queryExecution.execSelect()

            ResultSetFormatter.out(System.out, results, query)

            queryExecution.close()
        }
    }
}
