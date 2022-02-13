@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class RunCommand(
    private val jvmDebugger: JvmDebugger
) : REPLCommand(name = "run"), KoinComponent {
    val logger: Logger by inject()

    private val classOrSource: String by argument()

    override fun run() {
        val className =
            if (classOrSource.endsWith(".java")) {
                val sourcePath = Paths.get(classOrSource)

                val compiler = de.ahbnr.semanticweb.java_debugger.utils.Compiler(
                    listOf(sourcePath),
                    state.compilerTmpDir
                )

                logger.log("Compiling to ${state.compilerTmpDir}.")
                compiler.compile()
                logger.success("Compiled!")

                state.sourcePath = sourcePath
                state.classPath = state.compilerTmpDir

                sourcePath
                    .toString()
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