package de.ahbnr.semanticweb.java_debugger.repl.commands.utils

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isReadable

class SourceLocationParser : KoinComponent {
    private val logger: Logger by inject()

    data class SourceLocation(
        val className: String,
        val line: Int
    )

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
            logger.debug(errorContextExplanation)
            logger.error("Could not find source file $potentialSourcePath, or it is not readable.")
            return null
        }

        val file = potentialSourcePath.toFile()
        return file.useLines { lines ->
            val searchResult = lines
                .withIndex()
                .find { (_, line) -> line.contains(toFind) }

            if (searchResult == null) {
                logger.debug(errorContextExplanation)
                logger.error("Could not find search string \"$toFind\" in source file $potentialSourcePath.")
                return@useLines null
            }
            val foundLine = searchResult.index + 1

            logger.debug("Using line $foundLine of $potentialSourcePath where search string \"$toFind\" was found.")

            foundLine
        }
    }

    fun parse(locationString: String): SourceLocation? {
        val split = locationString.split(Regex(":"), 2)

        if (split.size != 2) {
            logger.error("Source location strings must contain a \":\" between the fully qualified class name and the line number OR search string.")
            return null
        }

        val (className, lineNumberOrSearchString) = split
        val lineNumber = lineNumberOrSearchString
            .toIntOrNull()
            ?: lineNumberByTextSearch(className, lineNumberOrSearchString)
            ?: return null

        return SourceLocation(className, lineNumber)
    }
}