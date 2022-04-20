package de.ahbnr.semanticweb.sjdb.repl.states

import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TimeCommandState {
    var lastCommandDuration: Duration? = null

    val savedDurations = mutableMapOf<String, Duration>()
}