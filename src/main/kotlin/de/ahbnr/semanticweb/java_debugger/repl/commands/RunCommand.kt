@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.file.Paths
import kotlin.io.path.pathString

class RunCommand(
    val jvmDebugger: JvmDebugger
) : IREPLCommand, KoinComponent {
    val logger: Logger by inject()

    override val name = "run"

    private val usage = """
        Usage: run <class or source file>
    """.trimIndent()

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val classOrSource = argv.firstOrNull()
        if (classOrSource == null) {
            logger.error(usage)
            return false
        }

        repl.sourcePath = null

        var classpath = Paths.get("") // CWD

        val className =
            if (classOrSource.endsWith(".java")) {
                val sourcePath = Paths.get(classOrSource)

                val compiler = de.ahbnr.semanticweb.java_debugger.utils.Compiler(
                    listOf(sourcePath),
                    repl.compilerTmpDir
                )

                logger.log("Compiling to ${repl.compilerTmpDir}.")
                compiler.compile()
                logger.success("Compiled!")

                repl.sourcePath = sourcePath
                classpath = repl.compilerTmpDir

                sourcePath
                    .toString()
                    .take(classOrSource.length - ".java".length)
                    .replace('/', '.')
            } else classOrSource

        logger.log("Launching Java program.")
        jvmDebugger.launchVM(className, classpath.pathString)
        jvmDebugger.jvm?.resume()

        return true
    }
}