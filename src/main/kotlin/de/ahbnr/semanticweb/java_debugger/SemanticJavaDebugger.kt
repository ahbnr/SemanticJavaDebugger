@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers.ClassMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers.ObjectMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.mappers.StackMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.genDefaultNs
import de.ahbnr.semanticweb.java_debugger.repl.JLineLogger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import de.ahbnr.semanticweb.java_debugger.repl.commands.*
import org.jline.terminal.TerminalBuilder
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.io.FileInputStream
import kotlin.system.exitProcess


class SemanticJavaDebugger : CliktCommand() {
    private val commandFile: String? by argument().optional()
    private val forceColor by option().switch(
        "--color" to "color",
        "--no-color" to "no-color"
    ).default("unknown")

    override fun run() {
        val terminalBuilder = TerminalBuilder.builder()
        if (forceColor != "unknown") {
            terminalBuilder.color(forceColor == "color")
        }

        val terminal = terminalBuilder.build()

        val ns = genDefaultNs()

        @Suppress("USELESS_CAST")
        startKoin {
            modules(
                module {
                    single { JLineLogger(terminal) as Logger }
                    single { OntURIs(ns) }
                }
            )
        }

        JvmDebugger().use { jvmDebugger ->
            val graphGen = GraphGenerator(
                ns,
                listOf(
                    ClassMapper(),
                    ObjectMapper(),
                    StackMapper()
                )
            )

            val repl = REPL(
                terminal,
                listOf(
                    AssertCommand(graphGen),
                    BuildKBCommand(jvmDebugger, graphGen),
                    CheckKBCommand(graphGen),
                    ContCommand(jvmDebugger),
                    DomainCommand(),
                    DumpKBCommand(),
                    InspectCommand(ns),
                    ReverseCommand(jvmDebugger, ns),
                    LocalsCommand(jvmDebugger),
                    RunCommand(jvmDebugger),
                    SectionCommand(),
                    ShaclCommand(graphGen),
                    SparqlCommand(graphGen),
                    StatsCommand(graphGen),
                    StopCommand(jvmDebugger),
                    TimeCommand()
                )
            )

            val wasSuccessful = if (commandFile != null) {
                val fileInputStream = FileInputStream(commandFile!!)

                repl.interpretStream(fileInputStream)
            } else {
                repl.main()
            }

            if (!wasSuccessful) {
                exitProcess(1)
            }
        }
    }
}

fun main(args: Array<String>) {
    SemanticJavaDebugger().main(args)
}