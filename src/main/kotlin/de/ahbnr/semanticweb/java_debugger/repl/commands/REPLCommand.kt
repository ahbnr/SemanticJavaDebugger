package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.CliktCommand
import de.ahbnr.semanticweb.java_debugger.repl.SemanticDebuggerState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class REPLCommand(name: String?) : CliktCommand(name = name),
    KoinComponent {
    protected val state: SemanticDebuggerState by inject()
}