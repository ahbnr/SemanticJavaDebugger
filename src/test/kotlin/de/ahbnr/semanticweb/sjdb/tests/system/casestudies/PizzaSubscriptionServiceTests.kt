package de.ahbnr.semanticweb.sjdb.tests.system.casestudies

import de.ahbnr.semanticweb.sjdb.tests.system.utils.runScriptTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.Ignore
import kotlin.test.assertEquals

class PizzaSubscriptionServiceTests {
    @Test
    fun `simple version of pizza case study`() {
        val exitCode = runScriptTest(
            Path.of("casestudies", "PizzaSubscriptionService"),
            Path.of("simple.sjdb")
        )

        assertEquals(0, exitCode)
    }
}