@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb;

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import de.ahbnr.semanticweb.jdi2owl.debugging.JvmDebugger
import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.sjdb.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.sjdb.rdf.mapping.datatypes.JavaAccessModifierDatatype
import de.ahbnr.semanticweb.sjdb.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.sjdb.rdf.mapping.forward.mappers.ClassMapper
import de.ahbnr.semanticweb.sjdb.rdf.mapping.forward.mappers.ObjectMapper
import de.ahbnr.semanticweb.sjdb.rdf.mapping.forward.mappers.StackMapper
import de.ahbnr.semanticweb.sjdb.rdf.mapping.genDefaultNs
import de.ahbnr.semanticweb.sjdb.repl.JLineLogger
import de.ahbnr.semanticweb.sjdb.repl.REPL
import de.ahbnr.semanticweb.sjdb.repl.SemanticDebuggerState
import de.ahbnr.semanticweb.sjdb.repl.commands.*
import org.apache.commons.io.FilenameUtils
import org.jline.terminal.TerminalBuilder
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.FileInputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess


/**
 * Entrypoint of sjdb
 *
 * <ul>
 * <li> Clikt-based CLI parameters
 * <li> Prepares JLine based interactive commandline
 * <li> Sets up koin dependency injection
 * <li> Starts REPL / runs sjdb-script automatically through REPL, line-by-line
 * </ul>
 */
class SemanticJavaDebugger : CliktCommand() {
    // Clikt CLI parameters
    private val commandFile: String? by argument().optional()
    private val forceColor by option().switch(
        "--color" to "color",
        "--no-color" to "no-color"
    ).default("unknown")

    // Will store exit code for whole program
    var exitCode: Int = 0
        private set

    override fun run() {
        // Initialize JLine
        val terminalBuilder = TerminalBuilder.builder()
        if (forceColor != "unknown") {
            terminalBuilder.color(forceColor == "color")
        }

        val terminal = terminalBuilder.build()

        // Setup temporary directories for storing compilation results etc.
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

        // Setup commonly used objects and make them available through dependency injection
        val ns = genDefaultNs()

        JvmDebugger().use { jvmDebugger ->
            // Setup dependency injection
            @Suppress("USELESS_CAST")
            startKoin {
                modules(
                    module {
                        single { JLineLogger(terminal) as Logger }
                        single { OntURIs(ns) }
                        single {
                            SemanticDebuggerState(
                                compilerTmpDir = compilerTmpDir
                            )
                        }
                        single { jvmDebugger }
                    }
                )
            }

            // Register custom datatypes with Jena
            JavaAccessModifierDatatype.register()

            val graphGen = GraphGenerator(
                ns,
                listOf(
                    ClassMapper(),
                    ObjectMapper(),
                    StackMapper()
                )
            )

            try {
                val readCommand = ReadCommand()

                // Initialize REPL and enable commands
                val repl = REPL(
                    terminal = terminal,
                    commands = listOf(
                        AddTriplesCommand(),
                        AssertCommand(),
                        BuildKBCommand(graphGen),
                        CheckKBCommand(),
                        ClassPathsCommand(),
                        CloseClass(),
                        ContCommand(),
                        DomainCommand(),
                        DumpKBCommand(),
                        InspectCommand(),
                        JdiLookupCommand(),
                        KillCommand(),
                        ReverseCommand(),
                        RhsChainCommand(),
                        LocalsCommand(),
                        LogCommand(),
                        MappingCommand(),
                        InferCommand(),
                        readCommand,
                        ReadKBCommand(),
                        ReasonerCommand(),
                        RunCommand(),
                        SectionCommand(),
                        ShaclCommand(graphGen),
                        SourcePathCommand(),
                        SparqlCommand(),
                        StatsCommand(),
                        StopCommand(graphGen),
                        TimeCommand()
                    )
                )
                readCommand.repl = repl

                // If a sjdb script file was supplied, run it line-by-line through the REPL
                val wasSuccessful = if (commandFile != null) {
                    val fileInputStream = FileInputStream(commandFile!!)

                    repl.interpretStream(fileInputStream)
                } else {
                    // Otherwise, run the REPL interactively
                    repl.main()
                    true
                }

                // Supply exit code to caller, base on result of REPL execution
                exitCode = if (wasSuccessful) 0 else -1
            } finally {
                // Shut down dependency injection system
                stopKoin()
            }
        }
    }
}

fun main(args: Array<String>) {
    val debugger = SemanticJavaDebugger()

    debugger.main(args)

    exitProcess(debugger.exitCode)
}