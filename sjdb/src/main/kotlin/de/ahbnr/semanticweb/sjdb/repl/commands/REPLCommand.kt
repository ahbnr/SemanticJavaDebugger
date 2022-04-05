package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import de.ahbnr.semanticweb.jdi2owl.debugging.JvmDebugger
import de.ahbnr.semanticweb.jdi2owl.debugging.JvmInstance
import de.ahbnr.semanticweb.jdi2owl.debugging.JvmState
import de.ahbnr.semanticweb.jdi2owl.Logger
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import de.ahbnr.semanticweb.sjdb.repl.SemanticDebuggerState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class REPLCommand(name: String?) : CliktCommand(name = name),
    KoinComponent {

    protected val logger: Logger by inject()
    protected val jvmDebugger: JvmDebugger by inject()
    protected val state: SemanticDebuggerState by inject()

    fun tryGetJvm(): JvmInstance {
        val jvm = jvmDebugger.jvm
        if (jvm == null) {
            logger.error("No JVM is running.")
            throw ProgramResult(-1)
        }

        return jvm
    }

    fun tryGetJvmState(): JvmState {
        val jvm = tryGetJvm()

        val jvmState = jvm.state
        if (jvmState == null) {
            logger.error("JVM is currently not paused.")
            throw ProgramResult(-1)
        }

        return jvmState
    }

    fun tryGetKnowledgeBase(): KnowledgeBase {
        val knowledgeBase = state.knowledgeBase
        if (knowledgeBase == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            throw ProgramResult(-1)
        }

        return knowledgeBase
    }
}
