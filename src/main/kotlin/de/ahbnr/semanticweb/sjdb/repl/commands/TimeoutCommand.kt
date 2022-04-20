@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.check
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.types.long
import org.koin.core.component.KoinComponent

class TimeoutCommand: REPLCommand(
    name = "timeout",
    help = "set timeout after which any REPL command is forcefully stoppped."
), KoinComponent {
    private val timeout by argument()
        .long()
        .help("Timeout in seconds. If set to 0, then no timeout is applied.")
        .check("Timeout values must be >= 0.") {
            it >= 0
        }

    override fun run() {
        state.timeout = this.timeout

        if (state.timeout == 0L) {
            logger.debug("Deactivated timeout.")
        }
    }
}