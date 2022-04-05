package de.ahbnr.semanticweb.sjdb.repl

import de.ahbnr.semanticweb.jdi2owl.Logger
import org.jline.terminal.Terminal
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import org.jline.utils.WriterOutputStream
import java.io.OutputStream
import java.nio.charset.Charset

class JLineLogger(
    private val terminal: Terminal
) : Logger {
    private fun printWithStyle(line: String, style: AttributedStyle?, appendNewline: Boolean) {
        val toPrint = if (style != null) {
            AttributedStringBuilder()
                .style(style)
                .append(line)
                .style(AttributedStyle.DEFAULT)
                .toAnsi(terminal)
        } else line

        if (appendNewline)
            terminal.writer().println(toPrint)
        else
            terminal.writer().print(toPrint)

        terminal.flush()
    }

    override fun debug(line: String, appendNewline: Boolean) {
        printWithStyle(
            line,
            AttributedStyle.DEFAULT.foreground(146, 131, 116),
            appendNewline
        )
    }

    override fun log(line: String, appendNewline: Boolean) {
        printWithStyle(
            line,
            null,
            appendNewline
        )
    }

    override fun emphasize(line: String, appendNewline: Boolean) {
        printWithStyle(
            line,
            AttributedStyle
                .DEFAULT
                .foreground(AttributedStyle.CYAN)
                .italic(),
            appendNewline
        )
    }

    override fun success(line: String, appendNewline: Boolean) {
        printWithStyle(
            line,
            AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN),
            appendNewline
        )
    }

    override fun warning(line: String, appendNewline: Boolean) {
        printWithStyle(
            line,
            AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW),
            appendNewline
        )
    }

    override fun error(line: String, appendNewline: Boolean) {
        printWithStyle(
            line,
            AttributedStyle.DEFAULT.foreground(AttributedStyle.RED),
            appendNewline
        )
    }

    override fun logStream(): OutputStream {
        return WriterOutputStream(terminal.writer(), Charset.defaultCharset())
    }

    override fun warningStream(): OutputStream {
        // FIXME: no color here
        return WriterOutputStream(terminal.writer(), Charset.defaultCharset())
    }

    override fun errorStream(): OutputStream {
        // FIXME: no color here
        return WriterOutputStream(terminal.writer(), Charset.defaultCharset())
    }

    override fun successStream(): OutputStream {
        // FIXME: no color here
        return WriterOutputStream(terminal.writer(), Charset.defaultCharset())
    }
}
