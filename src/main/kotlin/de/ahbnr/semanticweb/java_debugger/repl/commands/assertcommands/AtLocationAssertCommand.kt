@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands.assertcommands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import de.ahbnr.semanticweb.java_debugger.repl.commands.REPLCommand
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.SourceLocationParser
import org.koin.core.component.KoinComponent

class AtLocationAssertCommand : REPLCommand(name = "at"), KoinComponent {
    private val sourceLocation: String by argument()

    override fun run() {
        val sourceLocationParser = SourceLocationParser()
        val parsedLocation = sourceLocationParser
            .parse(sourceLocation)
            ?: throw ProgramResult(-1)

        val jvm = jvmDebugger.jvm
        if (jvm == null) {
            logger.log("Expected to be at location $parsedLocation.")
            logger.log("But no JVM is running.")
            logger.error("FAILED!")
            throw ProgramResult(-1)
        }

        val jvmState = jvm.state
        if (jvmState == null) {
            logger.log("Expected to be at location $parsedLocation.")
            logger.log("But JVM is currently not paused.")
            logger.error("FAILED!")
            throw ProgramResult(-1)
        }

        val actualClass = jvmState.location.declaringType().name()
        if (actualClass != parsedLocation.className) {
            logger.log("Expected to be at location $parsedLocation.")
            logger.log("But program is paused in class ${jvmState.location.sourceName()} instead.")
            logger.error("FAILED!")
            throw ProgramResult(-1)
        }

        val actualLine = jvmState.location.lineNumber()
        if (actualLine == -1) {
            logger.log("Expected to be at location $parsedLocation.")
            logger.log("But line number information is not available.")
            logger.error("FAILED!")
        }

        if (actualLine != parsedLocation.line) {
            logger.log("Expected to be at location $parsedLocation.")
            logger.log("But program is at line ${jvmState.location.lineNumber()} instead.")
            logger.error("FAILED!")
            throw ProgramResult(-1)
        }

        logger.success("PASSED")
    }
}