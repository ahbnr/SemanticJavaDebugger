package de.ahbnr.semanticweb.sjdb.tests.system.casestudies

import de.ahbnr.semanticweb.sjdb.tests.system.utils.runScriptTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.Ignore
import kotlin.test.assertEquals

class BTreeIterationTests {
    @Test
    fun `B-Tree iteration case study`() {
        val exitCode = runScriptTest(
            Path.of("casestudies", "btrees"),
            Path.of("BTreeIteration.sjdb")
        )

        assertEquals(0, exitCode)
    }
}