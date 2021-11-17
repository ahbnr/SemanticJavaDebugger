package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.repl.REPL

interface IREPLCommand {
    val name: String
    fun handleInput(argv: List<String>, rawInput: String, repl: REPL)
}