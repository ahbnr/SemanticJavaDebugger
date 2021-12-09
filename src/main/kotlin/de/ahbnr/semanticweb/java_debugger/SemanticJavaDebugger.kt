@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger;

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
import org.apache.commons.io.FilenameUtils
import org.jline.terminal.TerminalBuilder
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.FileInputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess


class SemanticJavaDebugger : CliktCommand() {
    private val commandFile: String? by argument().optional()
    private val forceColor by option().switch(
        "--color" to "color",
        "--no-color" to "no-color"
    ).default("unknown")

    var returnCode: Int = 0
        private set

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

        try {
            JvmDebugger().use { jvmDebugger ->
                val graphGen = GraphGenerator(
                    ns,
                    listOf(
                        ClassMapper(),
                        ObjectMapper(),
                        StackMapper()
                    )
                )

                val systemTmpDir = System.getProperty("java.io.tmpdir")
                val applicationTmpDir = Path.of(systemTmpDir, "SemanticJavaDebugger")

                val compilerTmpDir =
                    applicationTmpDir.resolve(
                        if (commandFile != null) {
                            if (Path.of(commandFile).isAbsolute) {
                                Path.of(FilenameUtils.getBaseName(commandFile))
                            } else {
                                Path.of(
                                    FilenameUtils.getPath(commandFile),
                                    FilenameUtils.getBaseName(commandFile)
                                )
                            }
                        } else Path.of("repl")
                    )
                compilerTmpDir.createDirectories()

                val repl = REPL(
                    terminal = terminal,
                    commands = listOf(
                        AssertCommand(graphGen),
                        BuildKBCommand(jvmDebugger, graphGen),
                        CheckKBCommand(graphGen),
                        ContCommand(jvmDebugger),
                        DomainCommand(),
                        DumpKBCommand(),
                        InspectCommand(ns),
                        ReverseCommand(jvmDebugger, ns),
                        LocalsCommand(jvmDebugger),
                        OwlClassCommand(),
                        RunCommand(jvmDebugger),
                        SectionCommand(),
                        ShaclCommand(graphGen),
                        SparqlCommand(graphGen),
                        StatsCommand(graphGen),
                        StopCommand(jvmDebugger),
                        TimeCommand()
                    ),
                    compilerTmpDir = compilerTmpDir
                )

                val wasSuccessful = if (commandFile != null) {
                    val fileInputStream = FileInputStream(commandFile!!)

                    repl.interpretStream(fileInputStream)
                } else {
                    repl.main()
                    true
                }

                returnCode = if (wasSuccessful) 0 else -1
            }
        } finally {
            stopKoin()
        }
    }
}

fun main(args: Array<String>) {
    val debugger = SemanticJavaDebugger()

    debugger.main(args)

    exitProcess(debugger.returnCode)
}