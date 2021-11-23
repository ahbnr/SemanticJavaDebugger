@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.GraphGenerator
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class StatsCommand(
    private val graphGenerator: GraphGenerator
) : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    override val name = "stats"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL) {
        val ontology = repl.knowledgeBase
        if (ontology == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            return
        }

        logger.log("Number of ontology axioms: ${ontology.axiomCount}")

        val model = graphGenerator.buildInferredModel(ontology)
        //var numStatements = 0
        //for (statement in model.listStatements()) {
        //    ++numStatements
        //}
        //logger.log("Number of statements in Jena Model: $numStatements")
        logger.log("")

        data class Countable(val name: String, val uri: String)

        val toCount = listOf(
            Countable("Classes", URIs.java.Class),
            Countable("Methods", URIs.java.Method),
            Countable("Fields", URIs.java.Field),
            Countable("Variable Declarations", URIs.java.VariableDeclaration),
            Countable("Objects", URIs.java.Object),
            Countable("Stack Frames", URIs.java.StackFrame),
        )

        val typeProperty = model.getProperty(URIs.rdf.type)
        fun countSubjects(countable: Countable): Int {
            val subjects = model.listSubjectsWithProperty(typeProperty, model.getResource(countable.uri))
            var count = 0
            for (subject in subjects) ++count

            return count
        }

        toCount.forEach {
            val count = countSubjects(it)

            logger.log("${it.name}: $count")
        }
    }
}