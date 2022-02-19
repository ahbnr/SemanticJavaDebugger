@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import de.ahbnr.semanticweb.java_debugger.rdf.linting.LinterMode
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.*

class StopCommand(
    val graphGenerator: GraphGenerator,
) : REPLCommand(name = "stop") {
    sealed class BreakpointCondition(val expression: String) {
        sealed class OwlDlCondition(expression: String) : BreakpointCondition(expression) {
            sealed class SatisfiabilityCondition(classExpression: String) : OwlDlCondition(classExpression) {
                class IfSatisfiableCondition(classExpression: String) : SatisfiabilityCondition(classExpression)
                class IfUnsatisfiableCondition(classExpression: String) : SatisfiabilityCondition(classExpression)
            }

            sealed class EntailmentCondition(axiomExpression: String) : OwlDlCondition(axiomExpression) {
                class IfEntailedCondition(axiomExpression: String) : EntailmentCondition(axiomExpression)
                class IfNotEntailedCondition(axiomExpression: String) : EntailmentCondition(axiomExpression)
            }
        }

        sealed class SparqlCondition(sparqlExpression: String) : BreakpointCondition(sparqlExpression) {
            class IfSparqlAnyCondition(sparqlExpression: String) : SparqlCondition(sparqlExpression)
            class IfSparqlNoneCondition(sparqlExpression: String) : SparqlCondition(sparqlExpression)
        }
    }

    inner class AtSubCommand : REPLCommand(name = "at") {
        private val sourceLocation: String by argument()

        private val condition: BreakpointCondition? by mutuallyExclusiveOptions(
            option("--if-satisfiable").convert {
                BreakpointCondition.OwlDlCondition.SatisfiabilityCondition.IfSatisfiableCondition(
                    it
                )
            },
            option("--if-unsatisfiable").convert {
                BreakpointCondition.OwlDlCondition.SatisfiabilityCondition.IfUnsatisfiableCondition(
                    it
                )
            },
            option("--if-entailed").convert {
                BreakpointCondition.OwlDlCondition.EntailmentCondition.IfEntailedCondition(
                    it
                )
            },
            option("--if-not-entailed").convert {
                BreakpointCondition.OwlDlCondition.EntailmentCondition.IfNotEntailedCondition(
                    it
                )
            },
            option("--if-sparql-any").convert { BreakpointCondition.SparqlCondition.IfSparqlAnyCondition(it) },
            option("--if-sparql-none").convert { BreakpointCondition.SparqlCondition.IfSparqlNoneCondition(it) }
        )

        val close: List<String> by option().multiple()

        override fun run() {
            val sourceLocationParser = SourceLocationParser()
            val parsedSourceLocation =
                sourceLocationParser
                    .parse(sourceLocation)
                    ?: throw ProgramResult(-1)

            val callback = condition?.let { breakpointCondition ->
                val closeInstance = close
                fun(): Boolean {
                    val jvm = this@StopCommand.jvmDebugger.jvm
                    if (jvm == null) {
                        this@StopCommand.logger.error("No JVM is available while checking conditional breakpoint. This should never happen")
                        return true
                    }

                    val jvmState = jvm.state
                    if (jvmState == null) {
                        this@StopCommand.logger.error("JVM is currently not paused. This should never happen while checking a conditional breakpoint.")
                        return true
                    }

                    val builder = KnowledgeBaseBuilder(
                        graphGenerator = this@StopCommand.graphGenerator,
                        jvmState = jvmState,
                        debuggerState = state,
                        linterMode = LinterMode.NoLinters,
                        quiet = true
                    )

                    val knowledgeBase = builder.build()
                    if (knowledgeBase == null) {
                        this@StopCommand.logger.error("Could not construct knowledge base for conditional breakpoint.")
                        return true
                    }

                    val classCloser = ClassCloser(
                        knowledgeBase,
                        noReasoner = false,
                        doSyntacticExtraction = false,
                        classRelationDepth = -1,
                        quiet = true
                    )
                    for (classToClose in closeInstance) {
                        classCloser.close(classToClose)
                    }

                    return when (breakpointCondition) {
                        is BreakpointCondition.OwlDlCondition -> {
                            val evaluator = OwlExpressionEvaluator(knowledgeBase, quiet = true)

                            when (breakpointCondition) {
                                is BreakpointCondition.OwlDlCondition.SatisfiabilityCondition -> {
                                    val isSatisfiable = evaluator.isSatisfiable(breakpointCondition.expression)
                                    if (isSatisfiable == null) {
                                        this@StopCommand.logger.error("Could not evaluate class expression for conditional breakpoint.")
                                        return true
                                    }

                                    when (breakpointCondition) {
                                        is BreakpointCondition.OwlDlCondition.SatisfiabilityCondition.IfSatisfiableCondition -> {
                                            if (isSatisfiable) {
                                                this@StopCommand.logger.log("`${breakpointCondition.expression}` is satisfiable at $parsedSourceLocation.")
                                                this@StopCommand.logger.emphasize("Conditional breakpoint hit!")
                                            }

                                            isSatisfiable
                                        }

                                        is BreakpointCondition.OwlDlCondition.SatisfiabilityCondition.IfUnsatisfiableCondition -> {
                                            if (!isSatisfiable) {
                                                this@StopCommand.logger.log("`${breakpointCondition.expression}` is not satisfiable at $parsedSourceLocation.")
                                                this@StopCommand.logger.emphasize("Conditional breakpoint hit!")
                                            }

                                            !isSatisfiable
                                        }
                                    }
                                }

                                is BreakpointCondition.OwlDlCondition.EntailmentCondition -> {
                                    val isEntailed = evaluator.isEntailed(breakpointCondition.expression)
                                    if (isEntailed == null) {
                                        this@StopCommand.logger.error("Could not evaluate axiom expression for conditional breakpoint.")
                                        return true
                                    }

                                    when (breakpointCondition) {
                                        is BreakpointCondition.OwlDlCondition.EntailmentCondition.IfEntailedCondition -> {
                                            if (isEntailed) {
                                                this@StopCommand.logger.log("`${breakpointCondition.expression}` is entailed at $parsedSourceLocation.")
                                                this@StopCommand.logger.emphasize("Conditional breakpoint hit!")
                                            }

                                            isEntailed
                                        }

                                        is BreakpointCondition.OwlDlCondition.EntailmentCondition.IfNotEntailedCondition -> {
                                            if (!isEntailed) {
                                                this@StopCommand.logger.log("`${breakpointCondition.expression}` is not entailed at $parsedSourceLocation.")
                                                this@StopCommand.logger.emphasize("Conditional breakpoint hit!")
                                            }

                                            !isEntailed
                                        }
                                    }
                                }
                            }
                        }

                        is BreakpointCondition.SparqlCondition -> {
                            val executor = SparqlExecutor(
                                knowledgeBase,
                                moduleExtractionOptions = ModuleExtractionOptions.NoExtraction
                            )

                            val execution = executor.execute(breakpointCondition.expression)
                            if (execution == null) {
                                this@StopCommand.logger.error("Could not evaluate sparql expression for conditional breakpoint.")
                                return true
                            }

                            val hasSolution = execution.use {
                                it.execSelect().hasNext()
                            }

                            when (breakpointCondition) {
                                is BreakpointCondition.SparqlCondition.IfSparqlAnyCondition -> {
                                    if (hasSolution) {
                                        this@StopCommand.logger.log("The SPARQL expression")
                                        this@StopCommand.logger.log(breakpointCondition.expression)
                                        this@StopCommand.logger.log("...yields results at $parsedSourceLocation")
                                        this@StopCommand.logger.emphasize("Conditional breakpoint hit!")
                                    }

                                    hasSolution
                                }

                                is BreakpointCondition.SparqlCondition.IfSparqlNoneCondition -> {
                                    if (!hasSolution) {
                                        this@StopCommand.logger.log("The SPARQL expression")
                                        this@StopCommand.logger.log(breakpointCondition.expression)
                                        this@StopCommand.logger.log("...yields no results at $parsedSourceLocation")
                                        this@StopCommand.logger.emphasize("Conditional breakpoint hit!")
                                    }

                                    !hasSolution
                                }
                            }
                        }
                    }
                }
            }

            this@StopCommand.jvmDebugger.setBreakpoint(
                parsedSourceLocation.className,
                parsedSourceLocation.line,
                callback
            )
        }

    }

    init {
        subcommands(AtSubCommand())
    }

    override fun run() {}
}