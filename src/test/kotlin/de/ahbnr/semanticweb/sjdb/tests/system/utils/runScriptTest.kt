package de.ahbnr.semanticweb.sjdb.tests.system.utils

import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.pathString

fun runScriptTest(workingDir: Path, scriptPath: Path): Int {
    // Running the tests in a subprocess instead of subthread for isolation

    val javaHome = System.getProperty("java.home")
    val javaBin = Path.of(javaHome, "bin", "java").pathString
    val classpath = System.getProperty("java.class.path")

    val command = listOf(
        javaBin,
        "-cp", classpath,
        "--add-opens", "jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
        "de.ahbnr.semanticweb.sjdb.SemanticJavaDebuggerKt",
        "--color",
        scriptPath.toString()
    )

    val process = ProcessBuilder(command)
        .directory( workingDir.absolute().toFile() )
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectInput(ProcessBuilder.Redirect.INHERIT)
        .inheritIO()
        .start()

    process.waitFor()

    return process.exitValue()
}