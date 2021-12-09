package de.ahbnr.semanticweb.java_debugger.tests.system

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.pathString
import kotlin.streams.asStream
import kotlin.test.assertEquals


class ScriptTests {
    @ParameterizedTest
    @ArgumentsSource(ScriptProvider::class)
    @Execution(ExecutionMode.CONCURRENT)
    fun scriptTest(scriptPath: Path) {
        // Running the tests in a subprocess instead of subthread for isolation

        val javaHome = System.getProperty("java.home")
        val javaBin = Path.of(javaHome, "bin", "java").pathString
        val classpath = System.getProperty("java.class.path")

        val command = listOf(
            javaBin,
            "-cp", classpath,
            "--add-opens", "jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
            "de.ahbnr.semanticweb.java_debugger.SemanticJavaDebuggerKt",
            "--color",
            scriptPath.toString()
        )

        val process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectInput(ProcessBuilder.Redirect.INHERIT)
            .inheritIO()
            .start()

        process.waitFor();


        // val debugger = SemanticJavaDebugger()

        // debugger.main(
        //     arrayOf(
        //         "--color",
        //         scriptPath.toString()
        //     )
        // )

        assertEquals(0, process.exitValue(), "Debugger script failed.")
    }

    class ScriptProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Path.of("examples", "tests")
                .toFile()
                .walkTopDown()
                .filter { it.isFile && it.path.endsWith(".sjd") }
                .map {
                    Arguments.of(it.toPath())
                }
                .asStream()
        }
    }
}