@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.apache.commons.lang3.time.DurationFormatUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.ExperimentalTime

class TimeCommand(
) : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()

    override val name = "time"

    @OptIn(ExperimentalTime::class)
    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val commandDuration = repl.lastCommandDuration
        if (commandDuration == null) {
            logger.error("You must first run a command to check its execution time.")
            return false
        }

        logger.log(
            "${
                DurationFormatUtils.formatDuration(
                    commandDuration.inWholeMilliseconds,
                    "HH'h' mm'm' ss's' SSS'ms'"
                )
            } (${commandDuration.toIsoString()})"
        )

        return true
    }
}