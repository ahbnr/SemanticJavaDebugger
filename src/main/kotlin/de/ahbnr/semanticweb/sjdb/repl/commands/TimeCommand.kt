@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.Gson
import org.apache.commons.lang3.time.DurationFormatUtils
import org.koin.core.component.KoinComponent
import java.io.File
import kotlin.time.ExperimentalTime

class TimeCommand: REPLCommand(name = "time"), KoinComponent {
    private val tag: String? by option()
    private val dumpTaggedToJson: File?  by option().file(canBeDir = false)

    @OptIn(ExperimentalTime::class)
    override fun run() {
        val commandDuration = state.timeCommandState.lastCommandDuration
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

        tag?.let {
            state.timeCommandState.savedDurations[it] = commandDuration
        }

        dumpTaggedToJson?.apply {
            val serialized = Gson().toJson(
                state
                    .timeCommandState
                    .savedDurations
                    .mapValues { it.value.toIsoString() }
                    .toMap()
            )
            writeText(serialized)

            logger.debug("Saved ${state.timeCommandState.savedDurations.size} durations to $absolutePath.")
        }
    }
}