@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.debugging.JvmDebugger
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.file.Paths

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

        val className =
            if (classOrSource.endsWith(".java")) {
                val sourcePath = Paths.get(classOrSource)

                val compiler = de.ahbnr.semanticweb.java_debugger.utils.Compiler(
                    listOf(sourcePath),
                    Paths.get("") // CWD
                )

                logger.log("Compiling...")
                compiler.compile()
                logger.success("Compiled!")

                sourcePath
                    .toString()
                    .take(classOrSource.length - ".java".length)
                    .replace('/', '.')
            } else classOrSource

        logger.log("Launching Java program.")
        jvmDebugger.launchVM(className)
        jvmDebugger.jvm?.resume()

        return true
    }
}