@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.subcommands
import de.ahbnr.semanticweb.sjdb.repl.commands.assertcommands.*
import org.koin.core.component.KoinComponent

class AssertCommand : REPLCommand(name = "assert"), KoinComponent {
    init {
        subcommands(
            SparqlAssertCommand(),
            TriplesAssertCommand(),
            InferAssertCommand(),
            AtLocationAssertCommand(),
            VariableAssertCommand(),
            ShaclAssertCommand()
        )
    }

    override fun run() {}
}