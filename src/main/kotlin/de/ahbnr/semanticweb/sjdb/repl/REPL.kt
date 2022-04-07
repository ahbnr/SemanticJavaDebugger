@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import de.ahbnr.semanticweb.jdi2owl.Logger
import de.ahbnr.semanticweb.sjdb.repl.commands.REPLCommand
import net.harawata.appdirs.AppDirsFactory
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.DefaultParser
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.Terminal
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sun.misc.Signal
import sun.misc.SignalHandler
import java.io.InputStream
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

private sealed class Mode {
    object Normal : Mode()

    // see https://en.wikipedia.org/wiki/Here_document#Unix_shells
    data class HereDoc(
        val delimiterId: String,
        val originalLine: String,
        val insertIdx: Int
    ) : Mode() {
        val hereDocBuffer: StringBuilder = StringBuilder()
    }
}

class REPL(
    private val terminal: Terminal,
    commands: List<REPLCommand>,
) : KoinComponent {
    private val state: SemanticDebuggerState by inject()

    private var mode: Mode = Mode.Normal

    private val commandMap = commands.map { it.commandName to it }.toMap()
    private val reader: LineReader
    private val historyPath: Path

    init {
        // Get the system cache directory, so that we can store the REPL command history in there
        val cacheDir = Path.of(AppDirsFactory.getInstance().getUserCacheDir("SemanticJavaDebugger", null, null) ?: ".")
        cacheDir.createDirectories()

        historyPath = cacheDir.resolve("history")

        reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .parser(DefaultParser())
            .variable(LineReader.HISTORY_FILE, historyPath)
            .history(DefaultHistory())
            .build()
    }

    private val prompt = AttributedStringBuilder()
        .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
        .append("> ")
        .style(AttributedStyle.DEFAULT)
        .toAnsi(terminal)

    private val logger: Logger by inject()

    private val hereDocRegex = """<<\s*([A-z]+)""".toRegex()

    private val lineParser = ReplLineParser()

    @OptIn(ExperimentalTime::class)
    private fun interpretLine(line: String): Boolean {
        // Dont do anything if the line is empty / whitespace
        if (line.isBlank())
            return true

        /**
         * The REPL works in two modes:
         *
         * Normal Mode: We interpret one command per line
         * HereDoc Mode: Sometimes input may span over multiple lines. This mode consumes them all and then passes the
         *   result to the last entered command.
         */
        val lastMode = mode
        return when (lastMode) {
            is Mode.Normal -> {
                // Remove initial whitespace
                val trimmedLine = line.trimStart()

                // Ignore comment lines starting with #
                if (trimmedLine.startsWith("#"))
                    return true

                // If we find a HereDoc (e.g. <<EOF) then there will be input that spans multiple lines.
                // Then we switch to HereDoc mode and consume these lines
                val hereDocMatch = hereDocRegex.find(line)
                if (hereDocMatch != null) {
                    mode = Mode.HereDoc(
                        delimiterId = hereDocMatch.groupValues[1],
                        originalLine = line.substring(0 until hereDocMatch.range.first) + line.substring(hereDocMatch.range.last + 1 until line.length),
                        insertIdx = hereDocMatch.range.first
                    )
                    return true
                }

                val argv = lineParser.parse(line)

                val commandName = argv.firstOrNull()

                val command = commandMap.getOrDefault(commandName, null)
                if (command == null) {
                    logger.error("No such command: ${argv[0]}")
                    return false
                }

                val (returnValue, duration) = measureTimedValue {
                    try {
                        command.parse(argv.drop(1))
                        true
                    } catch (e: ProgramResult) {
                        e.statusCode == 0
                    } catch (e: UsageError) {
                        logger.error(e.helpMessage())
                        false
                    } catch (e: CliktError) {
                        logger.log(e.stackTraceToString())
                        logger.error(e.message ?: "Unknown command execution error.")
                        false
                    }
                }

                state.lastCommandDuration = duration
                terminal.flush()

                returnValue
            }

            is Mode.HereDoc -> {
                val delimiterIdx = line.indexOf(lastMode.delimiterId)

                if (delimiterIdx < 0) {
                    lastMode.hereDocBuffer.append(line).append('\n')
                    true
                } else {
                    lastMode.hereDocBuffer.append(line.substring(0 until delimiterIdx))
                    lastMode.hereDocBuffer.insert(0, lastMode.originalLine.substring(0 until lastMode.insertIdx))
                    lastMode.hereDocBuffer.append(lastMode.originalLine.substring(lastMode.insertIdx))

                    mode = Mode.Normal
                    interpretLine(lastMode.hereDocBuffer.toString())
                }
            }
        }
    }

    /**
     * Non-interactively interprets all lines in input stream
     */
    fun interpretStream(input: InputStream): Boolean {
        for (line in input.bufferedReader().lineSequence()) {
            if (mode is Mode.Normal) {
                terminal.writer().print(prompt)
            }
            terminal.writer().println(line)
            terminal.flush()
            if (!interpretLine(line)) {
                return false
            }
        }

        return true
    }

    /**
     * Interactively interprets user input
     */
    fun main() {
        logger.debug("Storing command history in ${historyPath}.")

        var readInput = true
        while (readInput) {
            try {
                val line = reader.readLine(prompt)

                // We run all commands in a separate thread and wait for it on the main thread.
                //
                // Why? Because some commands, in particular those involving reasoners can take a long time and we want
                // the REPL to respond to Ctrl-C so that such commands can be interrupted.
                // Therefore, we register a listener for this interrupt signal and kill the command thread if the signal
                // is ever received.
                class Handler(private val commandThread: Thread) : SignalHandler {
                    override fun handle(p0: Signal?) {
                        @Suppress("DEPRECATION")
                        commandThread.stop()
                    }
                }

                val commandThread = thread(false) {
                    // Interpret an input line
                    // (this might actually consume multiple input lines, if the line contains a HereDoc, e.g. <<EOF)
                    interpretLine(line)
                }

                // Store the original signal handler and set our custom one
                val oldSignalHandler = Signal.handle(Signal("INT"), Handler(commandThread))

                // Run the command and wait for it on the main thread
                commandThread.start()
                commandThread.join()

                // Restore original signal handler
                Signal.handle(Signal("INT"), oldSignalHandler)

            } catch (e: UserInterruptException) {
                logger.debug("Send EOF (Ctrl-D) to exit.")
            } catch (e: EndOfFileException) {
                readInput = false
            }
        }
    }
}
