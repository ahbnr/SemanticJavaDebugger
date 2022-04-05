@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long
import de.ahbnr.semanticweb.sjdb.backwardmapping.utils.JavaObjectPrinter
import org.koin.core.component.KoinComponent

class JdiLookupCommand : REPLCommand(name = "jdilookup"), KoinComponent {
    val objectId: Long by argument("unique object ID").long()

    override fun run() {
        val jvmState = tryGetJvmState()

        when (val objectRef = jvmState.getObjectById(objectId)) {
            null -> {
                logger.error("There is no Java object with this id.")
                throw ProgramResult(-1)
            }
            else -> {
                val printer = JavaObjectPrinter(jvmState)

                printer.print(objectRef)
            }
        }
    }
}