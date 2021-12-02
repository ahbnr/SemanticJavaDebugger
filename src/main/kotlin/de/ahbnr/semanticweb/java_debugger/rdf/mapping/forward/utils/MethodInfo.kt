package de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.utils

import com.sun.jdi.AbsentInformationException
import com.sun.jdi.LocalVariable
import com.sun.jdi.Method
import de.ahbnr.semanticweb.java_debugger.logging.Logger
import de.ahbnr.semanticweb.java_debugger.rdf.mapping.forward.BuildParameters
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spoon.reflect.code.CtBlock
import spoon.reflect.code.CtLocalVariable
import spoon.reflect.cu.position.NoSourcePosition
import spoon.reflect.declaration.CtElement
import spoon.reflect.path.CtPathStringBuilder
import spoon.reflect.visitor.filter.LocalVariableScopeFunction
import spoon.reflect.visitor.filter.TypeFilter

class MethodInfo(
    val jdiMethod: Method,
    private val buildParameters: BuildParameters
) : KoinComponent {
    val logger: Logger by inject()

    private val jdiVariables: List<LocalVariable> by lazy {
        (if (!jdiMethod.isAbstract && !jdiMethod.isNative)
            try {
                jdiMethod.variables()
            } catch (e: AbsentInformationException) {
                if (AbsentInformationPackages.none { jdiMethod.declaringType().name().startsWith(it) }) {
                    logger.debug("Unable to get variables for $jdiMethod. This can happen for native and abstract methods.")
                }
                null
            }
        else null)
            ?: listOf()
    }

    private val body: CtBlock<*>? by lazy {
        val referenceType = jdiMethod.declaringType()

        val path = CtPathStringBuilder().fromString(
            ".${referenceType.name()}#method[signature=${jdiMethod.name()}(${
                jdiMethod.argumentTypeNames().joinToString(",")
            })]#body"
        )

        path
            .evaluateOn<CtBlock<*>>(buildParameters.sourceModel.rootPackage)
            .firstOrNull()
    }

    private var _jdiToSourceCache: ArrayListValuedHashMap<LocalVariable, CtLocalVariable<*>>? = null
    private var _sourceToJdiCache: ArrayListValuedHashMap<CtLocalVariable<*>, LocalVariable>? = null
    private fun computeVariableMappings() {
        val jdiToSource = ArrayListValuedHashMap<LocalVariable, CtLocalVariable<*>>()
        val sourceToJdi = ArrayListValuedHashMap<CtLocalVariable<*>, LocalVariable>()

        val sourceVariables = body?.getElements(TypeFilter(CtLocalVariable::class.java)) ?: listOf()

        for (jdiVariable in jdiVariables) {
            for (sourceVariable in sourceVariables) {
                val jdiMinScopeLine = InternalJDIUtils.getScopeStart(jdiVariable).lineNumber()

                val scopeElements = sourceVariable
                    .map(LocalVariableScopeFunction())
                    .list<CtElement>()
                    .filter { it.position !is NoSourcePosition }
                val sourceMinScopeLine = sourceVariable.position.line
                val sourceMaxScopeLine = scopeElements.maxOf { it.position.endLine }

                if (jdiMinScopeLine >= 0 && jdiVariable.name() == sourceVariable.simpleName && (sourceMinScopeLine..sourceMaxScopeLine).contains(
                        jdiMinScopeLine
                    )
                ) {
                    // FIXME: I am probably overdoing it here.
                    //   A JDI line position for a variable whose name is shared by variables in disjoint scopes can fall
                    //   onto the scope of multiple source variables (all in one line) and vice versa
                    //   (when the disjoint scopes are on one line)
                    //   -
                    //   However, in the one line case, we can not differentiate them anyway
                    jdiToSource.put(jdiVariable, sourceVariable)
                    sourceToJdi.put(sourceVariable, jdiVariable)
                }
            }
        }

        _jdiToSourceCache = jdiToSource
        _sourceToJdiCache = sourceToJdi
    }

    private val jdiToSource: ArrayListValuedHashMap<LocalVariable, CtLocalVariable<*>> by lazy {
        val cache = _jdiToSourceCache
        if (cache != null) cache
        else {
            computeVariableMappings()
            _jdiToSourceCache!!
        }
    }
    private val sourceToJdi: ArrayListValuedHashMap<CtLocalVariable<*>, LocalVariable> by lazy {
        val cache = _sourceToJdiCache
        if (cache != null) cache
        else {
            computeVariableMappings()
            _sourceToJdiCache!!
        }
    }

    private fun findSources(
        sourceVariables: List<CtLocalVariable<*>>,
        simpleName: String,
        groupedJdiVars: List<LocalVariable>
    ): Map<LocalVariable, CtLocalVariable<*>?> {
        val sameNameSources = sourceVariables.filter { it.simpleName == simpleName }

        // If all variable instances present in the source are also present at runtime, we just have to match them
        // in order
        // FIXME: This depends on the implementation of Comparable for LocalVariables of the used JDI implementation
        //   it should be correct for the JDI shipped with Java 11..
        if (sameNameSources.size == groupedJdiVars.size) {
            return groupedJdiVars
                .sorted()
                .zip(sameNameSources.sortedBy { it.position.sourceStart })
                .toMap()
        }

        // It seems some variables with the same name have been omitted by the compiler.
        // Lets try to match them by their location.
        else {
            return groupedJdiVars.associateWith {
                val sourceCandidates = jdiToSource[it]!!

                val associatedSource =
                    if (sourceCandidates.size == 1) {
                        val sourceCandidate = sourceCandidates.first()

                        val jdiCandidates = sourceToJdi[sourceCandidate]!!
                        if (jdiCandidates.size == 1) sourceCandidate
                        else null
                    } else null

                associatedSource
            }
        }
    }

    val variables: List<LocalVariableInfo> by lazy {
        val sourceVariables = body?.getElements(TypeFilter(CtLocalVariable::class.java)) ?: listOf()

        jdiVariables
            .groupBy { it.name() }
            .flatMap { (variableName, groupedVariables) ->
                val sourceAssociation = findSources(sourceVariables, variableName, groupedVariables)

                if (groupedVariables.size == 1) {
                    val variable = groupedVariables.first()
                    val association = sourceAssociation[variable]

                    listOf(LocalVariableInfo(variableName, variable, jdiMethod, association))
                } else {
                    groupedVariables
                        .mapIndexed { variableIndex, variable ->
                            LocalVariableInfo(
                                "${variableName}_$variableIndex",
                                variable,
                                jdiMethod,
                                sourceAssociation[variable]
                            )
                        }
                }
            }
    }
}