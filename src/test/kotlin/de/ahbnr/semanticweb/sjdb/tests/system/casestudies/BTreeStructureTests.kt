package de.ahbnr.semanticweb.sjdb.tests.system.casestudies

import de.ahbnr.semanticweb.sjdb.tests.system.utils.runScriptTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.Ignore
import kotlin.test.assertEquals

class BTreeStructureTests {
    @Test
    fun `B-Tree structure case study`() {
        val exitCode = runScriptTest(
            Path.of("casestudies", "btrees"),
            Path.of("StructureTest.sjdb")
        )

        assertEquals(0, exitCode)
    }
}