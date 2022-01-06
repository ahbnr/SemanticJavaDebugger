package de.ahbnr.semanticweb.java_debugger.tests.system

import de.ahbnr.semanticweb.java_debugger.tests.system.utils.runScriptTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

class BTreeStructureTests {
    @Test
    fun testKnuthConditions() {
        runScriptTest(
            Path.of("examples", "btrees", "tests", "btree-structure.sjd")
        )
    }
}