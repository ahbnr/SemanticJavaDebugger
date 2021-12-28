@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.apache.commons.lang3.time.DurationFormatUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.ExperimentalTime

class TimeCommand(
) : REPLCommand(name = "time"), KoinComponent {
    private val logger: Logger by inject()

    @OptIn(ExperimentalTime::class)
    override fun run() {
        val commandDuration = state.lastCommandDuration
        if (commandDuration == null) {
            logger.error("You must first run a command to check its execution time.")
            throw ProgramResult(-1)
        }

        logger.log(
            "${
                DurationFormatUtils.formatDuration(
                    commandDuration.inWholeMilliseconds,
                    "HH'h' mm'm' ss's' SSS'ms'"
                )
            } (${commandDuration.toIsoString()})"
        )
    }
}