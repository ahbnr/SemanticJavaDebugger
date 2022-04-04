package de.ahbnr.semanticweb.sjdb.repl.commands.utils

import com.github.owlcs.ontapi.OntManagers
import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.sjdb.logging.Logger
import de.ahbnr.semanticweb.sjdb.repl.KnowledgeBase
import org.apache.jena.graph.Node
import org.apache.jena.graph.Node_URI
import org.apache.jena.sparql.syntax.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.owlapi.model.OWLEntity
import uk.ac.manchester.cs.owlapi.modularity.ModuleType
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor
import kotlin.streams.toList

class SyntacticLocalityModuleExtractor(
    val knowledgeBase: KnowledgeBase,
    val depth: Int,
    val quiet: Boolean
) : KoinComponent {
    private val logger: Logger by inject()

    fun computeQuerySignature(pattern: Element): Set<OWLEntity> {
        val ontology = knowledgeBase.ontology
        val extractedAxioms = mutableSetOf<OWLEntity>()

        fun extractFromNode(node: Node) {
            if (node is Node_URI) {
                extractedAxioms.addAll(
                    ontology.unsortedSignature().filter { owlEntity ->
                        // If we have to differentiate between entities, maybe use a visitor
                        // owlEntity.accept(owlSignatureEntityVisitor)

                        node.hasURI(owlEntity.iri.toString())
                    }.toList()
                )
            }
        }

        // Traverse query bottom up
        // https://jena.apache.org/documentation/query/manipulating_sparql_using_arq.html#navigating-and-tinkering-visitors
        ElementWalker.walk(pattern, object : ElementVisitor {
            override fun visit(el: ElementGroup) {
                // recursion should be handled by walker
            }

            override fun visit(el: ElementPathBlock) {
                for (triplePath in el.pattern) {
                    if (!triplePath.isTriple) {
                        // TODO: Deal with paths https://jena.apache.org/documentation/query/property_paths.html
                        TODO("Not yet implemented")
                    }

                    extractFromNode(triplePath.subject)
                    extractFromNode(triplePath.predicate)
                    extractFromNode(triplePath.`object`)
                }
            }

            override fun visit(el: ElementTriplesBlock) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementFilter) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementAssign) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementBind) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementData) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementUnion) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementOptional) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementDataset) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementNamedGraph) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementExists) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementNotExists) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementMinus) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementService) {
                TODO("Not yet implemented")
            }

            override fun visit(el: ElementSubQuery) {
                TODO("Not yet implemented")
            }
        })

        return extractedAxioms
    }

    fun extractModule(pattern: Element): Ontology {
        val querySignature = computeQuerySignature(pattern)

        return extractModule(querySignature)
    }

    // https://www.javatips.net/api/Owl-master/owlapi-master/contract/src/test/java/org/semanticweb/owlapi/examples/Examples.java

    fun extractModule(signature: Set<OWLEntity>): Ontology {
        // RDFWriter
        //     .create(knowledgeBase.ontology.asGraphModel())
        //     .lang(Lang.TURTLE)
        //     .format(RDFFormat.TURTLE_PRETTY)
        //     .output("original.ttl")

        val extractor = SyntacticLocalityModuleExtractor(
            knowledgeBase.ontology.owlOntologyManager,
            knowledgeBase.ontology,
            ModuleType.STAR
            // The options for this parameter are somewhat explained in this article:
            //  "Modularity and OWL", Section 4.2.3, Clark & Parsia 2008, https://cbiit-download.nci.nih.gov/evs/Protege/Docs/ClassificationServicesProjectDocuments/v1.0/Reference/nci-modularity.pdf
        )

        return knowledgeBase
            .getSyntacticModuleExtractionReasoner()
            .use { reasoner ->
                if (!quiet)
                    logger.debug("Axioms before module extraction: ${knowledgeBase.ontology.axiomCount}.")
                val reducedAxioms = extractor.extract(signature, depth, depth, if (depth == 0) null else reasoner)
                if (!quiet)
                    logger.debug("Axioms after module extraction: ${reducedAxioms.size}.")

                val ontManager = OntManagers.createManager()
                val module = ontManager.createOntology(reducedAxioms) as Ontology

                // val printModel = module.asGraphModel()
                // printModel.setNsPrefixes(knowledgeBase.ontology.asGraphModel().nsPrefixMap)
                // RDFWriter
                //     .create(printModel)
                //     .lang(Lang.TURTLE)
                //     .format(RDFFormat.TURTLE_PRETTY)
                //     .output("reduced.ttl")

                module
            }
    }
}


