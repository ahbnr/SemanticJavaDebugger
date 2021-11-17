package de.ahbnr.semanticweb.java_debugger.debugging

import com.sun.jdi.ThreadReference

data class JVMState(
    val pausedThread: ThreadReference
)