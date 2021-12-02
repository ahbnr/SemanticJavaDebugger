@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl

import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.commands.IREPLCommand
import org.apache.jena.rdf.model.RDFNode
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.InputStream
import java.nio.file.Path
import kotlin.time.Duration
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
    commands: List<IREPLCommand>
) : KoinComponent {
    var applicationDomainDefFile: String? = null
    var sourcePath: Path? = null
    var knowledgeBase: Ontology? = null
    val namedNodes: MutableMap<String, RDFNode> = mutableMapOf()

    @OptIn(ExperimentalTime::class)
    var lastCommandDuration: Duration? = null

    private var mode: Mode = Mode.Normal

    private val commandMap = commands.map { it.name to it }.toMap()
    private val parser = DefaultParser()
    private val reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .parser(parser)
        .build()

    private val prompt = AttributedStringBuilder()
        .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
        .append("> ")
        .style(AttributedStyle.DEFAULT)
        .toAnsi(terminal)

    private val logger: Logger by inject()

    private val hereDocRegex = """<<\s*([A-z]+)""".toRegex()

    @OptIn(ExperimentalTime::class)
    private fun interpretLine(line: String): Boolean {
        if (line.isBlank()) {
            return true
        }

        val lastMode = mode
        return when (lastMode) {
            is Mode.Normal -> {
                val trimmedLine = line.trimStart()
                if (trimmedLine.startsWith("#")) {
                    return true // its a comment line
                }

                val hereDocMatch = hereDocRegex.find(line)
                if (hereDocMatch != null) {
                    mode = Mode.HereDoc(
                        delimiterId = hereDocMatch.groupValues[1],
                        originalLine = line.substring(0 until hereDocMatch.range.first) + line.substring(hereDocMatch.range.last + 1 until line.length),
                        insertIdx = hereDocMatch.range.first
                    )
                    return true
                }

                val argv = trimmedLine.split(' ')
                val commandName = argv.firstOrNull()

                val command = commandMap.getOrDefault(commandName, null)
                if (command == null) {
                    logger.error("No such command: ${argv[0]}")
                    return false
                }

                val (returnValue, duration) = measureTimedValue {
                    command.handleInput(
                        argv.drop(1),
                        trimmedLine.drop(commandName?.length ?: 0).trimStart(),
                        this
                    )
                }

                this.lastCommandDuration = duration
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

    fun main(): Boolean {
        var readInput = true
        while (readInput) {
            try {
                val line = reader.readLine(prompt)

                if (!interpretLine(line)) {
                    return false
                }
            } catch (e: UserInterruptException) {
                readInput = false
            } catch (e: EndOfFileException) {
                readInput = false
            }
        }

        return true
    }
}