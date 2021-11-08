package de.ahbnr.semanticweb.java_debugger.utils

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class ConcurrentLineCollector(vararg inputs: InputStream) {
    private data class WorkerMessage(val isWorkDone: Boolean, val line: String?)

    private val outputQueue = LinkedBlockingDeque<WorkerMessage>();
    private val executor = Executors.newFixedThreadPool(inputs.size)

    init {
        for (input in inputs) {
            val worker = Runnable {
                val buffer = BufferedReader(InputStreamReader(input))

                buffer.useLines { lines ->
                    lines.forEach {
                        outputQueue.addLast(WorkerMessage(false, it))
                    }
                }

                outputQueue.addLast(WorkerMessage(true, null))
            }

            executor.execute(worker)
        }
    }

    val seq = sequence<String> {
        var numTerminatedWorkers = 0

        do {
            val (isWorkDone, maybeLine) = outputQueue.takeFirst()

            if (isWorkDone) {
                ++numTerminatedWorkers
            }

            if (maybeLine != null) {
                yield(maybeLine)
            }
        } while(numTerminatedWorkers < inputs.size)

        executor.shutdown()
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            System.err.println("WARNING: Could not shut down line collector threads. Trying to force shutdown.")
            executor.shutdownNow()
        }
    }
}
