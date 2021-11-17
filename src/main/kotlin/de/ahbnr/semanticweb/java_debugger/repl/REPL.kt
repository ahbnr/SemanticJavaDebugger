@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.commands.IREPLCommand
import org.apache.jena.rdf.model.Model
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

class REPL(
    private val terminal: Terminal,
    commands: List<IREPLCommand>
): KoinComponent {
    var applicationDomainDefFile: String? = null
    var knowledgeBase: Model? = null

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

    private fun interpretLine(line: String) {
        val argv = line.split(' ')
        val commandName = argv.firstOrNull()
        val command = commandMap.getOrDefault(commandName, null)
        if (command != null) {
            command.handleInput(
                argv.drop(1),
                line.drop(commandName?.length ?: 0).trimStart(),
                this
            )
        }

        else {
            logger.error("No such command: ${argv[0]}")
        }

        terminal.flush()
    }

    fun interpretStream(input: InputStream) {
        input.bufferedReader().forEachLine { line ->
            terminal.writer().println("$prompt$line")
            terminal.flush()
            interpretLine(line)
        }
    }

    fun main() {
        var readInput = true
        while (readInput) {
            try {
                val line = reader.readLine(prompt)

                interpretLine(line)
            }

            catch (e: UserInterruptException) { readInput = false }
            catch (e: EndOfFileException) { readInput = false }
        }
    }
}