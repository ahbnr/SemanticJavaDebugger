@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent

class ReadCommand : REPLCommand(name = "read"), KoinComponent {
    var repl: REPL? = null

    val commandFile by argument().file(mustExist = true, mustBeReadable = true)

    override fun run() {
        if (repl?.interpretStream(commandFile.inputStream()) != true) {
            throw ProgramResult(-1)
        }
    }
}