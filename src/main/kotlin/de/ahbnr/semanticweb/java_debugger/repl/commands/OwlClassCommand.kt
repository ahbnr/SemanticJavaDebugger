@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.java_debugger.repl.commands

import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.OntURIs
import de.ahbnr.semanticweb.java_debugger.repl.REPL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.semanticweb.HermiT.ReasonerFactory
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxParserImpl

class OwlClassCommand : IREPLCommand, KoinComponent {
    private val logger: Logger by inject()
    private val URIs: OntURIs by inject()

    override val name = "owlclass"

    override fun handleInput(argv: List<String>, rawInput: String, repl: REPL): Boolean {
        val baseOntology = repl.knowledgeBase
        if (baseOntology == null) {
            logger.error("No knowledge base available. Run `buildkb` first.")
            return false
        }

        val manchesterParser = ManchesterOWLSyntaxParserImpl(
            baseOntology.owlOntologyManager.ontologyConfigurator,
            baseOntology.owlOntologyManager.owlDataFactory
        )
        manchesterParser.prefixManager.setPrefix("rdf", URIs.ns.rdf)
        manchesterParser.prefixManager.setPrefix("rdfs", URIs.ns.rdfs)
        manchesterParser.prefixManager.setPrefix("xsd", URIs.ns.xsd)
        manchesterParser.prefixManager.setPrefix("java", URIs.ns.java)
        manchesterParser.prefixManager.setPrefix("prog", URIs.ns.prog)
        manchesterParser.prefixManager.setPrefix("run", URIs.ns.run)

        val domainURI = baseOntology.asGraphModel().getNsPrefixURI("domain")
        if (domainURI != null) {
            manchesterParser.prefixManager.setPrefix("domain", domainURI)
        }
        manchesterParser.setDefaultOntology(baseOntology)

        val classExpression = manchesterParser.parseClassExpression(rawInput)
        val reasoner = ReasonerFactory().createReasoner(baseOntology)

        val instances = reasoner.getInstances(classExpression)
        if (instances.isEmpty) {
            logger.log("Found no instances for this class.")
        } else {
            for (instance in instances) {
                logger.log(instance.toString())
            }
        }

        return true
    }
}