package de.ahbnr.semanticweb.sjdb.repl.commands.infercommands

import com.github.ajalt.clikt.core.ProgramResult
import org.semanticweb.owlapi.apibinding.OWLFunctionalSyntaxFactory
import org.semanticweb.owlapi.model.OWLNamedIndividual

class IsClosedCommand : ExpressionSubCommand(name = "isClosed") {
    override fun run() {
        val evaluator = getEvaluator()

        // TODO: Can this be done more efficiently?

        val classExpression = evaluator.parseClassExpression(rawDlExpression) ?: throw ProgramResult(-1)

        val instances = evaluator
            .getInstances(classExpression)
            ?.toArray { size -> arrayOfNulls<OWLNamedIndividual>(size) }
            ?: throw ProgramResult(-1)

        val isClosedAxiom = OWLFunctionalSyntaxFactory.SubClassOf(
            classExpression,
            OWLFunctionalSyntaxFactory.ObjectOneOf(*instances)
        )

        val isClosed = evaluator.isEntailed(isClosedAxiom) ?: throw ProgramResult(-1)
        if (isClosed) {
            logger.success("true")
        } else {
            logger.error("false")
        }
    }
}