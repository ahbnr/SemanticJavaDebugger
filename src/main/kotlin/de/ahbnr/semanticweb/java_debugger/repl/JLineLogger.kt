package de.ahbnr.semanticweb.java_debugger.repl

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.jline.terminal.Terminal
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import org.jline.utils.WriterOutputStream
import java.io.OutputStream
import java.nio.charset.Charset

class JLineLogger(
    private val terminal: Terminal
): Logger {
    override fun log(line: String) {
        terminal.writer().println(line)
        terminal.flush()
    }

    override fun error(line: String) {
        terminal.writer().println(
            AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                .append(line)
                .style(AttributedStyle.DEFAULT)
                .toAnsi(terminal)
        )
        terminal.flush()
    }

    override fun logStream(): OutputStream {
        return WriterOutputStream(terminal.writer(), Charset.defaultCharset())
    }

    override fun errorStream(): OutputStream {
        // FIXME: no color here
        return WriterOutputStream(terminal.writer(), Charset.defaultCharset())
    }
}