@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.linting.LinterMode
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.ClassCloser
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.KnowledgeBaseBuilder
import de.ahbnr.semanticweb.java_debugger.repl.commands.utils.OwlExpressionEvaluator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isReadable

class StopCommand(
    val graphGenerator: GraphGenerator,
    val jvmDebugger: JvmDebugger
) : REPLCommand(name = "stop"), KoinComponent {
    val logger: Logger by inject()

    sealed class ClassCondition(val classExpression: String) {
        class IfCondition(classExpression: String) : ClassCondition(classExpression)
        class IfNotCondition(classExpression: String) : ClassCondition(classExpression)
    }

    inner class AtSubCommand : REPLCommand(name = "at") {
        private val classAndLine: String by argument()

        private val condition: ClassCondition? by mutuallyExclusiveOptions(
            option("--if").convert { ClassCondition.IfCondition(it) },
            option("--if-not").convert { ClassCondition.IfNotCondition(it) }
        )

        val limitSdk: Boolean by option().flag(default = false)
        val deep: List<String> by option().multiple()
        val close: List<String> by option().multiple()

        override fun run() {
            val split = classAndLine.split(Regex(":"), 2)

            if (split.size != 2) {
                this@StopCommand.logger.error(getFormattedUsage())
                throw ProgramResult(-1)
            }

            val (className, lineNumberOrSearchString) = split
            val lineNumber = lineNumberOrSearchString
                .toIntOrNull()
                ?: lineNumberByTextSearch(className, lineNumberOrSearchString)
                ?: throw ProgramResult(-1)

            val callback = condition?.let { classCondition ->
                val limitSdkInstance = limitSdk
                val deepInstance = deep
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
                        sourcePath = state.sourcePath,
                        applicationDomainDefFile = state.applicationDomainDefFile,
                        jvmState = jvmState,
                        limitSdk = limitSdkInstance,
                        deepFieldsAndVariables = deepInstance.toSet(),
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

                    val evaluator = OwlExpressionEvaluator(knowledgeBase, quiet = true)
                    val isSatisfiable = evaluator.isSatisfiable(classCondition.classExpression)
                    if (isSatisfiable == null) {
                        this@StopCommand.logger.error("Could not evaluate class expression for conditional breakpoint.")
                        return true
                    }

                    return when (classCondition) {
                        is ClassCondition.IfCondition -> {
                            if (isSatisfiable) {
                                this@StopCommand.logger.log("`${classCondition.classExpression}` is satisfiable at line $lineNumber.")
                                this@StopCommand.logger.emphasize("Conditional breakpoint hit!")
                            }

                            isSatisfiable
                        }

                        is ClassCondition.IfNotCondition -> {
                            if (!isSatisfiable) {
                                this@StopCommand.logger.log("`${classCondition.classExpression}` is not satisfiable at line $lineNumber.")
                                this@StopCommand.logger.emphasize("Conditional breakpoint hit!")
                            }

                            !isSatisfiable
                        }
                    }
                }
            }

            try {
                this@StopCommand.jvmDebugger.setBreakpoint(className, lineNumber, callback)
            } catch (e: NumberFormatException) {
                this@StopCommand.logger.error("Line number must be an integer: $lineNumber")
                throw ProgramResult(-1)
            }
        }

        private fun lineNumberByTextSearch(className: String, toFind: String): Int? {
            val errorContextExplanation = """
                    A string was given instead of a line number.
                    Will try to find the source file based on the class name and search its contents for the string.
                    The line number of the first appearance of the string will then be used.
                """.trimIndent()

            // Try to deduce source location from class expression.
            // Search the given string in the source and set the line number to the location of the string in the source
            val potentialSourcePath = Path.of(
                "${className.replace('.', File.separatorChar)}.java"
            )
            if (!potentialSourcePath.isReadable()) {
                this@StopCommand.logger.debug(errorContextExplanation)
                this@StopCommand.logger.error("Could not find source file $potentialSourcePath, or it is not readable.")
                return null
            }

            val file = potentialSourcePath.toFile()
            return file.useLines { lines ->
                val searchResult = lines
                    .withIndex()
                    .find { (_, line) -> line.contains(toFind) }

                if (searchResult == null) {
                    this@StopCommand.logger.debug(errorContextExplanation)
                    this@StopCommand.logger.error("Could not find search string \"$toFind\" in source file $potentialSourcePath.")
                    return@useLines null
                }
                val foundLine = searchResult.index + 1

                this@StopCommand.logger.debug("Using line $foundLine of $potentialSourcePath where search string \"$toFind\" was found.")

                foundLine
            }
        }
    }

    init {
        subcommands(AtSubCommand())
    }

    override fun run() {}
}