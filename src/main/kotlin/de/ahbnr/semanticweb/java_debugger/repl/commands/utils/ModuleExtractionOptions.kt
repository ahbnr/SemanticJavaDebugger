package de.ahbnr.semanticweb.java_debugger.repl.commands.utils

sealed class ModuleExtractionOptions {
    object NoExtraction : ModuleExtractionOptions()

    class SyntacticExtraction(
        var classRelationDepth: Int /* -1 means no subclass / superclass relations will be inspected */
    ) : ModuleExtractionOptions()
}