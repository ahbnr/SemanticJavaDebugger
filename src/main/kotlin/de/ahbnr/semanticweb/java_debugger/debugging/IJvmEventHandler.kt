package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.event.Event

sealed class HandleEventResult {
    object Nothing : HandleEventResult()
    object ForceResume : HandleEventResult()
}

interface IJvmEventHandler {
    fun handleEvent(jvm: JvmInstance, event: Event): HandleEventResult
}