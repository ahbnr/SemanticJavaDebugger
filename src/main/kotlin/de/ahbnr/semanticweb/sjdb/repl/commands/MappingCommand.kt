@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.subcommands
import de.ahbnr.semanticweb.sjdb.repl.commands.mappingcommands.SetCommand

class MappingCommand : REPLCommand(name = "mapping") {
    init {
        subcommands(
            SetCommand()
        )
    }

    override fun run() {}
}