@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.subcommands
import de.ahbnr.semanticweb.java_debugger.repl.commands.assertcommands.AtLocationAssertCommand
import de.ahbnr.semanticweb.java_debugger.repl.commands.assertcommands.InferAssertCommand
import de.ahbnr.semanticweb.java_debugger.repl.commands.assertcommands.SparqlAssertCommand
import de.ahbnr.semanticweb.java_debugger.repl.commands.assertcommands.TriplesAssertCommand
import org.koin.core.component.KoinComponent

class AssertCommand : REPLCommand(name = "assert"), KoinComponent {
    init {
        subcommands(
            SparqlAssertCommand(),
            TriplesAssertCommand(),
            InferAssertCommand(),
            AtLocationAssertCommand()
        )
    }

    override fun run() {}
}