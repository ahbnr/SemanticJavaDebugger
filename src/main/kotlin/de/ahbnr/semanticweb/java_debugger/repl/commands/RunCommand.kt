@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import org.koin.core.component.KoinComponent
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class RunCommand : REPLCommand(name = "run"), KoinComponent {
    private val classOrSource: String by argument()

    override fun run() {
        val className =
            if (classOrSource.endsWith(".java")) {
                val compiler = de.ahbnr.semanticweb.java_debugger.utils.Compiler(
                    listOf(Paths.get(classOrSource)),
                    state.compilerTmpDir
                )

                logger.log("Compiling to ${state.compilerTmpDir}.")
                compiler.compile()
                logger.success("Compiled!")

                state.sourcePath = Paths.get("") // CWD
                state.classPath = state.compilerTmpDir

                classOrSource
                    .take(classOrSource.length - ".java".length)
                    .replace('/', '.')
            } else classOrSource

        if (state.classPath == null) {
            state.classPath = Paths.get("") // CWD
        }

        logger.log("Launching Java program.")
        jvmDebugger.launchVM(className, state.classPath!!.absolutePathString())
        jvmDebugger.jvm?.resume()
    }
}