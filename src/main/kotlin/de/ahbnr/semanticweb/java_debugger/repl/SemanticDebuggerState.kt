package de.ahbnr.semanticweb.java_debugger.repl

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class SemanticDebuggerState(
    val compilerTmpDir: Path = Paths.get("") // CWD
) {
    var applicationDomainDefFile: String? = null
    var sourcePath: Path? = null
    var knowledgeBase: KnowledgeBase? = null
    var targetReasoner: ReasonerId = ReasonerId.PureJenaReasoner.JenaOwlMicro

    @OptIn(ExperimentalTime::class)
    var lastCommandDuration: Duration? = null
}