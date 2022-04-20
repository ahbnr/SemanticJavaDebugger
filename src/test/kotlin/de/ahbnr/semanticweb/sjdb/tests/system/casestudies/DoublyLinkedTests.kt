package de.ahbnr.semanticweb.sjdb.tests.system.casestudies

import de.ahbnr.semanticweb.sjdb.tests.system.utils.runScriptTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.Ignore
import kotlin.test.assertEquals

class DoublyLinkedTests {
    @Test
    fun `doubly-linked list case study that makes use of shacl`() {
        val exitCode = runScriptTest(
            Path.of("casestudies", "DoublyLinked"),
            Path.of("DoublyLinked.sjdb")
        )

        assertEquals(0, exitCode)
    }
}