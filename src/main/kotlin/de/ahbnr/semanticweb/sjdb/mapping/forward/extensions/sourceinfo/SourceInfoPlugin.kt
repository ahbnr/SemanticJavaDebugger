package de.ahbnr.semanticweb.sjdb.mapping.forward.extensions.sourceinfo

import de.ahbnr.semanticweb.jdi2owl.mapping.forward.Mapper
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.pluginapi.BaseMappingListener
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.pluginapi.MappingPlugin
import spoon.reflect.CtModel

class SourceInfoPlugin(
    private val sourceModel: CtModel
): MappingPlugin {
    override fun getListeners(): List<BaseMappingListener> = listOf(SourceInfoMappingListener(sourceModel))
    override fun getMappers(): List<Mapper> = emptyList()
}