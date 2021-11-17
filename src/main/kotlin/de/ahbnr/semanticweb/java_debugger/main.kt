@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import de.ahbnr.semanticweb.java_debugger.debugging.JVMDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.genDefaultNs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.mappers.ClassMapper
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.mappers.ObjectMapper
import de.ahbnr.semanticweb.java_debugger.repl.JLineLogger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import de.ahbnr.semanticweb.java_debugger.repl.commands.*
import org.jline.terminal.TerminalBuilder
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.io.FileInputStream


class SemanticJavaDebugger: CliktCommand() {
    val commandFile: String? by argument().optional()

    override fun run() {
        val terminal = TerminalBuilder
            .builder()
            .build()

        startKoin {
            modules(
                module {
                    single { JLineLogger(terminal) as Logger }
                }
            )
        }

        val jvmDebugger = JVMDebugger()

        val ns = genDefaultNs()
        val graphGen = GraphGenerator(
            listOf(
                ClassMapper(ns),
                ObjectMapper(ns)
            )
        )

        val repl = REPL(
            terminal,
            listOf(
                BuildKBCommand(jvmDebugger, graphGen),
                CheckKBCommand(),
                ContCommand(jvmDebugger),
                DomainCommand(),
                LocalsCommand(jvmDebugger),
                RunCommand(jvmDebugger),
                SparqlCommand(graphGen),
                StopCommand(jvmDebugger)
            )
        )

        if (commandFile != null) {
            val fileInputStream = FileInputStream(commandFile!!)

            repl.interpretStream(fileInputStream)
        }

        else {
            repl.main()
        }
    }
}

fun main(args: Array<String>) {
    SemanticJavaDebugger().main(args)
}