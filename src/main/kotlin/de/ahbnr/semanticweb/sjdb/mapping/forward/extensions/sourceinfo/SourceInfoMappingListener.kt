package de.ahbnr.semanticweb.sjdb.mapping.forward.extensions.sourceinfo

import de.ahbnr.semanticweb.jdi2owl.mapping.forward.base_mappers.MappingContext
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.base_mappers.component_maps.program_structure.MethodContext
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.pluginapi.BaseMappingListener
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.LocationInfo
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils.MethodInfo
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.NodeFactory
import spoon.reflect.CtModel
import spoon.reflect.cu.SourcePosition
import spoon.reflect.declaration.CtMethod
import spoon.reflect.path.CtPathStringBuilder

class SourceInfoMappingListener(
    private val sourceModel: CtModel
): BaseMappingListener {
    private fun getMethodSource(methodInfo: MethodInfo): CtMethod<*>? {
        val jdiMethod = methodInfo.jdiMethod
        val declaringType = jdiMethod.declaringType()

        val path = CtPathStringBuilder().fromString(
            ".${declaringType.name()}#method[signature=${jdiMethod.name()}(${
                jdiMethod.argumentTypeNames().joinToString(",")
            })]"
        )

        return path
            .evaluateOn<CtMethod<*>>(sourceModel.rootPackage)
            .firstOrNull()
    }

    private fun getDeclarationLocation(methodInfo: MethodInfo): LocationInfo? {
        val sourcePosition = getMethodSource(methodInfo)?.position
        return if (sourcePosition != null) {
            fromSourcePosition(sourcePosition)
        } else null
    }

    private fun fromSourcePosition(position: SourcePosition): LocationInfo {
        // This is often an absolute path.
        // A relative path would be nicer, but is not well-defined if there are multiple project roots
        val filePath = position.file.path

        return LocationInfo(
            "${filePath}_${position.sourceStart}",
            filePath,
            position.line
        )
    }

    private fun mapMethod(context: MethodContext) = with(context) {
        // add declaration location
        val declarationLocation = getDeclarationLocation(methodInfo)
        if (declarationLocation != null) {
            with (IRIs) {
                val locationIRI = prog.genLocationIRI(declarationLocation)

                tripleCollector.dsl {
                    locationIRI {
                        rdf.type of java.Location

                        java.isAtSourcePath of NodeFactory.createLiteralByValue(
                            declarationLocation.sourcePath,
                            XSDDatatype.XSDstring
                        )

                        java.isAtLine of NodeFactory.createLiteralByValue(
                            declarationLocation.line,
                            XSDDatatype.XSDint
                        )
                    }

                    methodIRI {
                        java.isDeclaredAt of locationIRI
                    }
                }
            }
        }
    }

    override fun mapInContext(context: MappingContext) =
        when (context) {
            is MethodContext -> mapMethod(context)
            else -> Unit
        }
}
