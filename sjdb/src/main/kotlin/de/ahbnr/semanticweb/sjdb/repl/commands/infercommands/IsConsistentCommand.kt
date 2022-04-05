package de.ahbnr.semanticweb.sjdb.repl.commands.infercommands

import de.ahbnr.semanticweb.sjdb.repl.commands.REPLCommand
import de.ahbnr.semanticweb.sjdb.repl.commands.utils.ConsistencyChecker

class IsConsistentCommand : REPLCommand(name = "isConsistent") {
    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        val checker = ConsistencyChecker(
            knowledgeBase = knowledgeBase
        )

        checker
            .check()
            .use { result ->
                when (result) {
                    is ConsistencyChecker.Result.Consistent -> logger.success("true")
                    is ConsistencyChecker.Result.Inconsistent -> logger.success("false")
                }
            }
    }
}