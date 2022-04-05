@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.subcommands
import de.ahbnr.semanticweb.sjdb.repl.commands.infercommands.*
import org.koin.core.component.KoinComponent

class InferCommand : REPLCommand(name = "infer"), KoinComponent {

    init {
        subcommands(
            ClassesOfCommand(),
            ClassificationCommand(),
            EntailsCommand(),
            InstancesOfCommand(),
            IsClosedCommand(),
            IsConsistentCommand(),
            IsSatisfiableCommand(),
            RealisationCommand(),
            SignatureCommand(),
            UnsatisfiableClassesCommand()
        )
    }

    override fun run() {}
}