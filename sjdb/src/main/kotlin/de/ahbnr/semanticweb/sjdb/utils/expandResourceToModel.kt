package de.ahbnr.semanticweb.sjdb.utils

import de.ahbnr.semanticweb.jdi2owl.mapping.Namespaces
import org.apache.jena.rdf.model.*

fun expandResourceToModel(baseResource: Resource, ns: Namespaces, noBlankDefaults: Boolean = true): Model {
    val targetModel: Model = ModelFactory.createDefaultModel()
    targetModel.setNsPrefixes(baseResource.model.nsPrefixMap)

    val addedResources = mutableSetOf<AnonId>()

    val typeUri = ns.rdf + "type"
    val blankNodeDefaultTypes = listOf(
        ns.owl + "Thing",
        ns.owl + "Class",
        ns.rdfs + "Resource",
        ns.rdfs + "Class",
    )

    fun addProperties(node: RDFNode): RDFNode? =
        node.visitWith(object : RDFVisitor {
            override fun visitBlank(blank: Resource, id: AnonId): Any? {
                if (addedResources.contains(blank.id)) {
                    return null
                }
                addedResources.add(blank.id)

                val blankCopy = targetModel.createResource()

                for (property in blank.listProperties()) {
                    if (
                        noBlankDefaults &&
                        property.predicate.hasURI(typeUri) &&
                        property.`object`.isResource &&
                        blankNodeDefaultTypes.any { property.`object`.asResource().hasURI(it) }
                    ) {
                        continue
                    }

                    val objectCopy = addProperties(property.`object`)

                    if (objectCopy != null) {
                        blankCopy.addProperty(property.predicate, objectCopy)
                    }
                }

                return blankCopy
            }

            override fun visitLiteral(l: Literal) = targetModel.createTypedLiteral(l.value, l.datatype)
            override fun visitURI(r: Resource, uri: String) = targetModel.createResource(uri)
        }) as RDFNode?

    val baseResourceCopy = targetModel.createResource(baseResource.uri)

    for (property in baseResource.listProperties()) {
        val objectCopy = addProperties(property.`object`)

        if (objectCopy != null) {
            baseResourceCopy
                .addProperty(property.predicate, objectCopy)
        }
    }

    // for (statement in baseResource.model.listStatements(SimpleSelector(null, null, baseResource))) {
    //     targetModel
    //         .createResource(statement.subject.uri)
    //         .addProperty(
    //             targetModel.createProperty(statement.predicate.uri),
    //             baseResourceCopy
    //         )
    // }

    return targetModel
}