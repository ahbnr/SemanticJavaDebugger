@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.sun.jdi.ObjectReference
import de.ahbnr.semanticweb.jdi2owl.mapping.OntIRIs
import de.ahbnr.semanticweb.sjdb.backwardmapping.BackwardMapper
import de.ahbnr.semanticweb.sjdb.backwardmapping.utils.JavaObjectPrinter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReverseCommand : REPLCommand(name = "reverse"), KoinComponent {
    private val URIs: OntIRIs by inject()
    private val variableOrIRI: String by argument()

    override fun run() {
        val jvmState = tryGetJvmState()
        val knowledgeBase = tryGetKnowledgeBase()

        val nodes = knowledgeBase.resolveVariableOrUri(variableOrIRI)
        if (nodes.isEmpty()) {
            logger.error("No such RDF node is known.")
            throw ProgramResult(-1)
        }

        val inverseMapping = BackwardMapper(jvmState)

        val printer = JavaObjectPrinter(jvmState)

        for ((identifier, node) in nodes) {
            logger.debug("$identifier:")

            when (val mapping = inverseMapping.map(node, knowledgeBase, knowledgeBase.buildParameters.limiter)) {
                is ObjectReference -> printer.print(mapping)
                else -> logger.error("Could not retrieve a Java construct for the given variable.")
            }

            logger.log("")
        }
    }
}