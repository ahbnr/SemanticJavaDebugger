@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.repl.KnowledgeBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.apibinding.OWLFunctionalSyntaxFactory
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.OWLObjectProperty

/**
 * Implementation of the construction for role inclusion axioms with right-hand-side property chains:
 *    "Complex Role Inclusions with Role Chains on the Right are Expressible in SROIQ",
 *    Compton,
 *    International Journal on Semantic Web and Information Systems, 11(1), 46-63, January-March 2015
 *
 * For now this implementation just exists to test out the construction.
 */
class RhsChainCommand : REPLCommand(name = "rhschain"), KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    val lhs: String by argument()
    val rhs1: String by argument()
    val rhs2: String by argument()
    val rhsRemainder: List<String> by argument().multiple()

    fun createRhsRIA(
        knowledgeBase: KnowledgeBase,
        lhs: OWLObjectProperty,
        rhs1: OWLObjectProperty,
        rhs2: OWLObjectProperty,
        rhsRemainder: List<OWLObjectProperty>,
        depth: Int = 0
    ) {
        when (val rhs3 = rhsRemainder.firstOrNull()) {
            null -> createRhsRIA(knowledgeBase, lhs, rhs1, rhs2)
            else -> {
                val currentTime = System.currentTimeMillis()
                val `iri of R'` = "${lhs.iri.iriString}'${depth}_$currentTime"
                val `R'` = OWLFunctionalSyntaxFactory.ObjectProperty(IRI.create(`iri of R'`))
                knowledgeBase.ontology.add(OWLFunctionalSyntaxFactory.Declaration(`R'`))

                createRhsRIA(knowledgeBase, `R'`, rhs1, rhs2)
                createRhsRIA(
                    knowledgeBase,
                    lhs,
                    `R'`,
                    rhs3,
                    rhsRemainder.drop(1),
                    depth + 1
                )
            }
        }
    }

    fun createRhsRIA(knowledgeBase: KnowledgeBase, R: OWLObjectProperty, S: OWLObjectProperty, T: OWLObjectProperty) {
        val ontology = knowledgeBase.ontology
        val currentTime = System.currentTimeMillis()

        // top concept
        val top = OWLFunctionalSyntaxFactory.Class(IRI.create(URIs.owl.Thing))

        val `iri of R` = R.iri.iriString
        val `iri of S` = S.iri.iriString
        val `iri of T` = T.iri.iriString

        // Add Sₗ, Sₗ⁻, Sᵣ
        val Sₗ = OWLFunctionalSyntaxFactory.ObjectProperty(IRI.create("${`iri of S`}Sₗ$currentTime"))
        val `Sₗ⁻` = OWLFunctionalSyntaxFactory.ObjectProperty(IRI.create("${`iri of S`}Sₗ⁻$currentTime"))
        val Sᵣ = OWLFunctionalSyntaxFactory.ObjectProperty(IRI.create("${`iri of S`}Sᵣ$currentTime"))

        ontology.add(OWLFunctionalSyntaxFactory.Declaration(Sₗ))
        ontology.add(OWLFunctionalSyntaxFactory.Declaration(`Sₗ⁻`))
        ontology.add(OWLFunctionalSyntaxFactory.Declaration(Sᵣ))

        // Add Tₗ, Tₗ⁻, Tᵣ
        val Tₗ = OWLFunctionalSyntaxFactory.ObjectProperty(IRI.create("${`iri of T`}Tₗ$currentTime"))
        val `Tₗ⁻` = OWLFunctionalSyntaxFactory.ObjectProperty(IRI.create("${`iri of T`}Tₗ⁻$currentTime"))
        val Tᵣ = OWLFunctionalSyntaxFactory.ObjectProperty(IRI.create("${`iri of T`}Tᵣ$currentTime"))

        ontology.add(OWLFunctionalSyntaxFactory.Declaration(Tₗ))
        ontology.add(OWLFunctionalSyntaxFactory.Declaration(`Tₗ⁻`))
        ontology.add(OWLFunctionalSyntaxFactory.Declaration(Tᵣ))

        // Add Q, Q⁻
        val Q = OWLFunctionalSyntaxFactory.ObjectProperty(IRI.create("${`iri of R`}Q$currentTime"))
        val `Q⁻` = OWLFunctionalSyntaxFactory.ObjectProperty(IRI.create("${`iri of R`}Q⁻$currentTime"))
        ontology.add(OWLFunctionalSyntaxFactory.Declaration(Q))
        ontology.add(OWLFunctionalSyntaxFactory.Declaration(`Q⁻`))

        // Sₗ⁻ ∘ Sᵣ ⊑ S
        ontology.add(OWLFunctionalSyntaxFactory.SubPropertyChainOf(listOf(`Sₗ⁻`, Sᵣ), S))

        // Tₗ⁻ ∘ Tᵣ ⊑ T
        ontology.add(OWLFunctionalSyntaxFactory.SubPropertyChainOf(listOf(`Tₗ⁻`, Tᵣ), T))

        // Q ∘ Tₗ ⊑ Sᵣ
        ontology.add(OWLFunctionalSyntaxFactory.SubPropertyChainOf(listOf(Q, Tₗ), Sᵣ))

        // Q⁻ ∘ Sₗ ∘ R ⊑ Tᵣ
        ontology.add(OWLFunctionalSyntaxFactory.SubPropertyChainOf(listOf(`Q⁻`, Sₗ, R), Tᵣ))

        // ∃R.⊤ ⊑ ∃Sₗ⁻.⊤
        ontology.add(
            OWLFunctionalSyntaxFactory.SubClassOf(
                OWLFunctionalSyntaxFactory.ObjectSomeValuesFrom(R, top),
                OWLFunctionalSyntaxFactory.ObjectSomeValuesFrom(`Sₗ⁻`, top),
            )
        )

        // ∃Q⁻.⊤ ⊑ ∃Tₗ.⊤
        ontology.add(
            OWLFunctionalSyntaxFactory.SubClassOf(
                OWLFunctionalSyntaxFactory.ObjectSomeValuesFrom(`Q⁻`, top),
                OWLFunctionalSyntaxFactory.ObjectSomeValuesFrom(Tₗ, top),
            )
        )

        // ∃Sₗ.⊤ ⊑ ∃Q.⊤
        ontology.add(
            OWLFunctionalSyntaxFactory.SubClassOf(
                OWLFunctionalSyntaxFactory.ObjectSomeValuesFrom(Sₗ, top),
                OWLFunctionalSyntaxFactory.ObjectSomeValuesFrom(Q, top),
            )
        )
    }

    override fun run() {
        val knowledgeBase = state.tryGetKnowledgeBase()

        val properties = (listOf(lhs, rhs1, rhs2) + rhsRemainder)
            .map {
                val resolvedIri = knowledgeBase.resolvePrefixNameInUri(it)
                val property = OWLFunctionalSyntaxFactory.ObjectProperty(IRI.create(resolvedIri))

                if (!knowledgeBase.ontology.isDeclared(property)) {
                    logger.error("No such property: $it.")
                    throw ProgramResult(-1)
                }

                property
            }

        createRhsRIA(
            knowledgeBase,
            properties[0],
            properties[1],
            properties[2],
            properties.drop(3)
        )

        logger.success("done.")
    }
}