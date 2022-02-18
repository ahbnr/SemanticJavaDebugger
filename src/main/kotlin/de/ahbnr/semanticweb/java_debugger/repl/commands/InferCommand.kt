@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.subcommands
import de.ahbnr.semanticweb.java_debugger.repl.commands.infercommands.*
import org.koin.core.component.KoinComponent

class InferCommand : REPLCommand(name = "infer"), KoinComponent {

    init {
        subcommands(
            ClassesOfCommand(),
            EntailsCommand(),
            InstancesOfCommand(),
            IsClosedCommand(),
            IsConsistentCommand(),
            IsSatisfiableCommand(),
            SignatureCommand(),
            UnsatisfiableClassesCommand()
        )
    }

    override fun run() {}
}