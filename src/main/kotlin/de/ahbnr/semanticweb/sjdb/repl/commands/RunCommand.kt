@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import de.ahbnr.semanticweb.jdi2owl.utils.Compiler
import org.koin.core.component.KoinComponent
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class RunCommand : REPLCommand(name = "run"), KoinComponent {
    private val classOrSource: String by argument()
    private val args: List<String> by argument().multiple()

    override fun run() {
        val className =
            if (classOrSource.endsWith(".java")) {
                val compiler = Compiler(
                    listOf(Paths.get(classOrSource)),
                    state.compilerTmpDir
                )

                logger.log("Compiling to ${state.compilerTmpDir}.")
                compiler.compile()
                logger.success("Compiled!")

                state.sourcePath = Path.of(classOrSource)
                state.classPaths = listOf(state.compilerTmpDir)

                classOrSource
                    .take(classOrSource.length - ".java".length)
                    .replace('/', '.')
            } else classOrSource

        val classPaths = state.classPaths.ifEmpty {
            listOf(Paths.get("")) // CWD
        }.map { it.absolutePathString() }

        val argsString = args.joinToString(" ")

        logger.log("Launching Java program.")
        jvmDebugger.launchVM("$className $argsString", classPaths)
        jvmDebugger.jvm?.resume()
    }
}