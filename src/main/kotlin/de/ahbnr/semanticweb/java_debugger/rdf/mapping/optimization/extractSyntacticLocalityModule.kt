package de.ahbnr.semanticweb.java_debugger.rdf.mapping.optimization

import com.github.owlcs.ontapi.OntManagers
import com.github.owlcs.ontapi.Ontology
import de.ahbnr.semanticweb.java_debugger.repl.KnowledgeBase
import org.apache.jena.graph.Node
import org.apache.jena.graph.Node_URI
import org.apache.jena.sparql.syntax.*
import org.semanticweb.owlapi.model.OWLEntity
import org.semanticweb.owlapi.model.OWLOntology
import uk.ac.manchester.cs.owlapi.modularity.ModuleType
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor
import kotlin.streams.toList

private fun computeQuerySignature(ontology: OWLOntology, pattern: Element): Set<OWLEntity> {
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

// https://www.javatips.net/api/Owl-master/owlapi-master/contract/src/test/java/org/semanticweb/owlapi/examples/Examples.java

fun extractSyntacticLocalityModule(knowledgeBase: KnowledgeBase, pattern: Element): Ontology {
    val querySignature = computeQuerySignature(knowledgeBase.ontology, pattern)

    // RDFWriter
    //     .create(knowledgeBase.ontology.asGraphModel())
    //     .lang(Lang.TURTLE)
    //     .format(RDFFormat.TURTLE_PRETTY)
    //     .output("original.ttl")

    val extractor = SyntacticLocalityModuleExtractor(
        knowledgeBase.ontology.owlOntologyManager,
        knowledgeBase.ontology,
        ModuleType.STAR
    )

    val reasoner = knowledgeBase.getSyntacticModuleExtractionReasoner()
    // val reasoner = JFactFactory().createReasoner(ontology)
    val reducedAxioms = extractor.extract(querySignature, -1, -1, reasoner)

    val ontManager = OntManagers.createManager()
    val module = ontManager.createOntology(reducedAxioms) as Ontology

    // val printModel = module.asGraphModel()
    // printModel.setNsPrefixes(knowledgeBase.ontology.asGraphModel().nsPrefixMap)
    // RDFWriter
    //     .create(printModel)
    //     .lang(Lang.TURTLE)
    //     .format(RDFFormat.TURTLE_PRETTY)
    //     .output("reduced.ttl")

    return module
}