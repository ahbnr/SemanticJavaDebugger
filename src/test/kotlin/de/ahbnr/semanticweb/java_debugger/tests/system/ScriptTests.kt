package de.ahbnr.semanticweb.java_debugger.tests.system

import SemanticJavaDebugger
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.streams.asStream
import kotlin.test.assertEquals

class ScriptTests {
    @ParameterizedTest
    @ArgumentsSource(ScriptProvider::class)
    fun scriptTest(scriptPath: Path) {
        val debugger = SemanticJavaDebugger()

        debugger.main(
            arrayOf(
                "--color",
                scriptPath.toString()
            )
        )

        assertEquals(0, debugger.returnCode, "Debugger script failed.")
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