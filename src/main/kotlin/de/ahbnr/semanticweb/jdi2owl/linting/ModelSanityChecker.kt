package de.ahbnr.semanticweb.jdi2owl.linting

import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.jdi2owl.mapping.MappingLimiter
import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.OntURIs
import openllet.core.vocabulary.BuiltinNamespace
import openllet.jena.BuiltinTerm
import openllet.pellint.lintpattern.LintPattern
import openllet.pellint.lintpattern.LintPatternLoader
import openllet.pellint.model.Lint
import org.apache.jena.rdf.model.Model
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.model.OWLLiteral
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.profiles.OWLProfileViolation
import org.semanticweb.owlapi.profiles.Profiles
import org.semanticweb.owlapi.profiles.violations.UseOfDefinedDatatypeInLiteral
import org.semanticweb.owlapi.profiles.violations.UseOfReservedVocabularyForClassIRI
import org.semanticweb.owlapi.profiles.violations.UseOfReservedVocabularyForIndividualIRI

enum class LinterMode {
    Normal,
    FullReport,
    NoLinters
}

class ModelSanityChecker : KoinComponent {
    private val URIs: OntURIs by inject()
    private val logger: Logger by inject()

    fun fullCheck(ontology: Ontology, mappingLimiter: MappingLimiter, linterMode: LinterMode) {
        if (linterMode == LinterMode.NoLinters)
            return

        val model = ontology.asGraphModel()

        checkRdfTyping(model)
        openllintOwlSyntaxChecks(model, mappingLimiter, linterMode == LinterMode.FullReport)
        OWL2DLProfileViolationTest(ontology)
        openllintOwlPatternChecks(ontology)
    }

    fun checkRdfTyping(model: Model) {
        val typeProperty = model.getProperty(URIs.rdf.type)

        var foundLint = false

        // We utilize the namespace definitions of Openllet here to check for typos etc in terms from well known namespaces like "owl:Restriction"
        model
            .listObjectsOfProperty(typeProperty)
            .forEach { obj ->
                val node = obj.asNode()

                val builtinTerm = BuiltinTerm.find(node)
                if (builtinTerm == null && node.isURI) {
                    val builtinNamespace = BuiltinNamespace.find(node.nameSpace)
                    if (builtinNamespace != null) {
                        logger.warning("Warning: The term ${node.localName} is not known in namespace ${node.nameSpace}.")
                        foundLint = true
                    }
                }
            }

        if (foundLint)
            logger.log("")

        // TODO: There is potentially a lot more sanity checks we can do
        //  (Typos, all the stuff that openllet.jena.graph.loader.DefaultGraphLoader is checking, ...)
    }

    // based on https://github.com/Galigator/openllet/blob/b7a07b60d2ae6a147415e30be0ffb72eff7fe857/tools-cli/src/main/java/openllet/Openllint.java#L280
    fun openllintOwlSyntaxChecks(model: Model, limiter: MappingLimiter, fullLintingReport: Boolean) {
        val checker = FilteredOwlSyntaxChecker(
            model,
            setOf(
                URIs.ns.rdfs,
                URIs.ns.rdf,
                URIs.ns.owl,
            )
        )
        val lints = checker.validate()
        if (!lints.isEmpty()) {
            if (lints.onlyUntypedJavaObjects && limiter.isLimiting() && !fullLintingReport) {
                logger.debug("Untyped java objects found by Openllint. This is likely caused by the shallow analysis and usually no reason for concern.")
            } else {
                lints.log()
            }

            logger.log("")
        }
    }

    // based on https://github.com/Galigator/openllet/blob/b7a07b60d2ae6a147415e30be0ffb72eff7fe857/tools-cli/src/main/java/openllet/Openllint.java#L315
    fun OWL2DLProfileViolationTest(ontology: OWLOntology) {
        val owl2Profile = Profiles.OWL2_DL

        // This linter is prone to throwing exceptions for the smallest anomalies in the input data
        val profileReport = try {
            owl2Profile.checkOntology(ontology)
        } catch (e: RuntimeException) {
            val message = e.message
            if (message != null) {
                logger.log(message)
            }
            logger.warning("Internal OWL2DL Profile linter error. This can indicate some anomaly in the input data.")
            logger.log("")
            null
        } ?: return

        val violationRemovalFilters: List<(OWLProfileViolation) -> Boolean> = listOf(
            // Protege produces `owl:Class rdf:type owl:Class` axioms, which are unfortunately detected as use of
            // reserved vocabulary
            { it is UseOfReservedVocabularyForIndividualIRI && it.expression.iri.iriString == URIs.owl.Class },
            { it is UseOfReservedVocabularyForClassIRI && it.expression.iri.iriString == URIs.owl.Class }
        )

        val violations = profileReport.violations
            .filterNot { violation ->
                violationRemovalFilters.any {
                    it(
                        violation
                    )
                }
            }
        if (violations.isNotEmpty()) {
            for (violation in violations) {
                logger.log(violation.toString())
                logger.log("  Affected axiom: ${violation.axiom}")

                if (violation is UseOfDefinedDatatypeInLiteral && violation.expression is OWLLiteral) {
                    logger.emphasize(
                        """
                        Are you defining a literal for a custom datatype?
                        Be aware that that user-defined datatypes have empty lexical spaces and thus must not appear in literals (https://www.w3.org/TR/owl2-syntax/#Datatype_Definitions).
                        
                        This means, that for e.g. a custom enumeration datatype, use the enumerated literals with their original typing instead of typing them with the custom datatype.
                        """.trimIndent()
                    )
                }
            }
            logger.error("Error: Openllint found OWL2 DL violations.")
            logger.log("")
        }
    }

    // based on https://github.com/Galigator/openllet/blob/b7a07b60d2ae6a147415e30be0ffb72eff7fe857/tools-cli/src/main/java/openllet/Openllint.java#L315
    fun openllintOwlPatternChecks(ontology: OWLOntology) {
        val patternLoader = LintPatternLoader()
        val ontologyLints = mutableMapOf<LintPattern, MutableList<Lint>>()

        val axiomIterator = ontology.axioms().iterator()
        while (true) {
            // OwlApi/OntApi is prone to throwing exceptions for the smallest anomalies in the input data
            val axiom = try {
                if (axiomIterator.hasNext()) {
                    axiomIterator.next()
                } else break
            } catch (e: RuntimeException) {
                val message = e.message
                if (message != null) {
                    logger.log(message)
                }
                logger.warning("Internal Openllint pattern checker error. This can indicate some anomaly in the input data.")
                logger.log("")
                null
            } ?: return

            for (pattern in patternLoader.axiomLintPatterns) {
                val lint = pattern.match(ontology, axiom)
                if (lint != null) {
                    val lintList = ontologyLints.getOrDefault(pattern, mutableListOf())
                    lintList.add(lint)
                    ontologyLints[pattern] = lintList
                }
            }
        }


        for (pattern in patternLoader.ontologyLintPatterns) {
            val lints = pattern.match(ontology)
            if (lints.isNotEmpty()) {
                val lintList = ontologyLints.getOrDefault(pattern, mutableListOf())
                lintList.addAll(lints)
                ontologyLints[pattern] = lintList
            }
        }

        if (ontologyLints.isNotEmpty()) {
            for (pattern in ontologyLints.keys) {
                logger.log("Pattern ${pattern.name}: ${pattern.description}")

                val lintFormat = pattern.defaultLintFormat
                for ((idx, lint) in ontologyLints[pattern]!!.withIndex()) {
                    logger.log("Lint #$idx: ${lintFormat.format(lint)}")
                    val fixer = lint.lintFixer
                    if (fixer != null) {
                        logger.log("  To fix the lint...")
                        val axiomsToAdd = fixer.axiomsToAdd
                        if (axiomsToAdd.isNotEmpty()) {
                            logger.log("  ...add these axioms:")
                            for (axiom in axiomsToAdd) {
                                logger.log("    $axiom")
                            }
                        }

                        val axiomsToRemove = fixer.axiomsToRemove
                        if (axiomsToRemove.isNotEmpty()) {
                            logger.log("  ...remove these axioms:")
                            for (axiom in axiomsToRemove) {
                                logger.log("    $axiom")
                            }
                        }
                    }
                }

            }

            logger.warning("Warning: Openllint detected modeling constructs that have a negative effect on reasoning performance.")
            logger.log("")
        }

        // FIXME: Also check imported ontologies
    }
}

