package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.Bootstrap
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.LocatableEvent
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.genDefaultNs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.mappers.ClassMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.mappers.ObjectMapper
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.ResultSetFormatter
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import java.io.FileOutputStream

class Debugger(private val classToDebug: String, private val lineNum: Int) {
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

    fun displayVariables(applicationDomainDefFile: String?, locatableEvent: LocatableEvent) {
        val stackFrame = locatableEvent.thread().frame(0)

        if (stackFrame.location().toString().contains(classToDebug)) {
            val visibleVariables = stackFrame.getValues(stackFrame.visibleVariables())

            println("Variables at " + stackFrame.location().toString().toString() + " > ")
            for ((key, value) in visibleVariables) {
                println(key.name().toString() + " = " + value)
            }

            println("RDF Graph")
            val ns = genDefaultNs()

            val graphGen = GraphGenerator(
                applicationDomainDefFile,
                listOf(
                    ClassMapper(ns),
                    ObjectMapper(ns)
                )
            )
            val model = graphGen.getGraphModel(locatableEvent.virtualMachine(), locatableEvent.thread())

            println("WRITING")
            val fileOut = FileOutputStream("graph.rdf")
            //model.write(fileOut)
            //model.write(System.out)
            //RDFDataMgr.write(fileOut, model, Lang.RDFXML)
            fileOut.close()

            println("QUERY")

            val query = QueryFactory.create("""
                PREFIX domain: <https://github.com/ahbnr/SemanticJavaDebugger/TwoThreeTree#>
                PREFIX prog: <https://github.com/ahbnr/SemanticJavaDebugger/Program#>
                PREFIX java: <https://github.com/ahbnr/SemanticJavaDebugger#>
                SELECT ?x
                WHERE { ?x a domain:Root }
            """.trimIndent())

            //val query = QueryFactory.create("""
            //    PREFIX domain: <https://github.com/ahbnr/SemanticJavaDebugger/TwoThreeTree#>
            //    PREFIX prog: <https://github.com/ahbnr/SemanticJavaDebugger/Program#>
            //    PREFIX java: <https://github.com/ahbnr/SemanticJavaDebugger#>
            //    SELECT ?x
            //    WHERE { ?x prog:Node_parent java:null }
            //""".trimIndent())

            val queryExecution = QueryExecutionFactory.create(query, model)
            println("EXECUTING QUERY")
            val results = queryExecution.execSelect()

            println("PRINTING RESULT")
            ResultSetFormatter.out(System.out, results, query)

            queryExecution.close()
        }
    }
}
