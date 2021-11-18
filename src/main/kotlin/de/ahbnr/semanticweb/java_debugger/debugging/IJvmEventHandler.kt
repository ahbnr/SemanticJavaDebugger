package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.event.Event

interface IJvmEventHandler {
    fun handleEvent(jvm: JvmInstance, event: Event)
}