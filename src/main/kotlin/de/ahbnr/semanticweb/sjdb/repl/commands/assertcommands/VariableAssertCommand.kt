@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands.assertcommands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import de.ahbnr.semanticweb.sjdb.repl.commands.REPLCommand
import de.ahbnr.semanticweb.sjdb.repl.commands.utils.SourceLocationParser
import org.koin.core.component.KoinComponent

class VariableAssertCommand : REPLCommand(
    name = "variable",
    help = """
        Succeeds if the given variable exists. Otherwise fails.
        The variable is usually the result of a SPARQL query etc.
        
        Usage example:
        
        > sparql 'SELECT ?i WHERE { ... }'
        > assert variable ?i
    """.trimIndent()
), KoinComponent {
    private val variableName: String by argument()

    override fun run() {
        val kb = tryGetKnowledgeBase()

        val variable = kb.getVariable(variableName)
        if (variable == null) {
            logger.error("FAILED!")
            throw ProgramResult(-1)
        }

        else {
            logger.success("PASSED")
        }
    }
}