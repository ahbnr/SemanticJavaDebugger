@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.Gson
import de.ahbnr.semanticweb.sjdb.utils.MemoryUsageMonitor

class MemoryCommand: REPLCommand(
    name = "memory",
    help = "Shows statistics about memory use. Only enabled if sjdb was called with the --monitor-memory flag."
) {
    init {
        subcommands(
            object: REPLCommand(name = "peak", help = "Shows the maximum memory use ever observed during the execution of this application. This only includes the memory that was truly used, not allocated memory.") {
                private val dumpJson by option().file(canBeDir = false)

                override fun run() {
                    val peakUseBytes = MemoryUsageMonitor.peakMemoryUse
                    if (peakUseBytes == null) {
                        logger.error("Can not retrieve peak memory use. Either it has not yet been measured, or you forgot to call sjdb with the --monitor-memory flag.")
                        return
                    }

                    logger.log("${peakUseBytes.div(1024 * 1024)} MiB (${peakUseBytes} bytes)")

                    dumpJson?.apply {
                        writeText(
                            Gson().toJson(
                                mapOf(
                                    "peak" to peakUseBytes
                                )
                            )
                        )
                    }
                }
            },
            object: REPLCommand(name = "clear", help = "Delete all memory statistics collected so far.") {
                override fun run() {
                    MemoryUsageMonitor.clear()
                }
            }
        )
    }

    override fun run() { }
}