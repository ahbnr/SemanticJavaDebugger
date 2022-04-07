package de.ahbnr.semanticweb.sjdb.tests.system

import de.ahbnr.semanticweb.sjdb.tests.system.utils.runScriptTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.Ignore
import kotlin.test.assertEquals

class BTreeStructureTests {
    @Ignore
    @Test
    fun testKnuthConditions() {
        val exitCode = runScriptTest(
            Path.of("examples", "btrees", "tests", "StructureTest.sjd")
        )

        assertEquals(0, exitCode)
    }
}