@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands.mappingcommands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.types.choice
import de.ahbnr.semanticweb.sjdb.repl.commands.REPLCommand

class SetCommand : REPLCommand(name = "set") {
    private val limitSdk = "limit-sdk"
    private val deep = "deep"
    private val noSequenceDescriptions = "no-sequence-descriptions"
    private val makeObjectsDistinct = "make-objects-distinct"

    private val setting: () -> Unit by argument().choice(
        limitSdk to { state.mappingSettings.limitSdk = tryGetBooleanValue() },
        deep to {
            state.mappingSettings.deepFieldsAndVariables.apply {
                val newDeep = tryGetSet()
                clear()
                addAll(newDeep)
            }
        },
        noSequenceDescriptions to { state.mappingSettings.noSequenceDescriptions = tryGetBooleanValue() },
        makeObjectsDistinct to { state.mappingSettings.makeObjectsDistinct = tryGetBooleanValue() }
    )
    private val values by argument().multiple()

    private fun tryGetBooleanValue(): Boolean =
        values
            .singleOrNull()
            ?.lowercase()
            ?.toBooleanStrictOrNull()
            .let {
                if (it == null) {
                    logger.error("Only a single \"true\" or \"false\" value is supported.")
                    throw ProgramResult(-1)
                } else it
            }

    private fun tryGetSet(): Set<String> = values.toSet()

    override fun run() {
        setting.invoke()
    }
}