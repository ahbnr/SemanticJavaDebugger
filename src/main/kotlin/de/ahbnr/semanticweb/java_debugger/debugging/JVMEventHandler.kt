package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.event.Event

interface JVMEventHandler {
    fun handleEvent(jvm: JVMInstance, event: Event)
}