package de.ahbnr.semanticweb.sjdb.tests.system

import de.ahbnr.semanticweb.sjdb.tests.system.utils.runScriptTest
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.relativeTo
import kotlin.streams.asStream
import kotlin.test.assertEquals


class ScriptTests {
    @ParameterizedTest
    @ArgumentsSource(ScriptProvider::class)
    @Execution(ExecutionMode.CONCURRENT)
    fun scriptTest(scriptPath: Path) {
        val workingDir = Path.of(
            "src", "test", "resources",
            "de", "ahbnr", "semanticweb", "sjdb", "tests", "system",
        )

        val exitValue = runScriptTest(
            workingDir,
            scriptPath.relativeTo(workingDir)
        )

        assertEquals(0, exitValue, "Debugger script failed.")
    }

    class ScriptProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Path.of(
                "src", "test", "resources",
                "de", "ahbnr", "semanticweb", "sjdb", "tests", "system",
                "examples", "tests"
            )
                .toFile()
                .walkTopDown()
                .filter { it.isFile && it.path.endsWith(".sjdb") }
                .map {
                    Arguments.of(it.toPath())
                }
                .asStream()
        }
    }
}