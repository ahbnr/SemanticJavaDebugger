package de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils

import com.sun.jdi.AbsentInformationException
import com.sun.jdi.LocalVariable
import com.sun.jdi.Method
import de.ahbnr.semanticweb.jdi2owl.debugging.utils.InternalJDIUtils
import de.ahbnr.semanticweb.jdi2owl.debugging.utils.getLocalMethodId
import de.ahbnr.semanticweb.logging.Logger
import de.ahbnr.semanticweb.jdi2owl.mapping.forward.BuildParameters
import org.apache.commons.collections4.MultiValuedMap
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spoon.reflect.code.CtBlock
import spoon.reflect.code.CtLocalVariable
import spoon.reflect.cu.position.NoSourcePosition
import spoon.reflect.declaration.CtElement
import spoon.reflect.declaration.CtMethod
import spoon.reflect.path.CtPathStringBuilder
import spoon.reflect.visitor.filter.LocalVariableScopeFunction
import spoon.reflect.visitor.filter.TypeFilter

class MethodInfo(
    val jdiMethod: Method,
    private val buildParameters: BuildParameters
) : KoinComponent {
    private val logger: Logger by inject()

    // we have to encode the return type to deal with bridge methods which can violate the overloading rules
    val id = getLocalMethodId(jdiMethod)

    val declarationLocation: LocationInfo?
        get() {
            val sourcePosition = methodSource?.position
            return if (sourcePosition != null) {
                LocationInfo.fromSourcePosition(sourcePosition)
            } else null
        }

    val definitionLocation: LocationInfo?
        get() {
            val jdiLocation = jdiMethod.location()
            return if (jdiLocation != null) {
                LocationInfo.fromJdiLocation(jdiLocation)
            } else null
        }

    val variables: List<LocalVariableInfo> by lazy {
        jdiVariables
            .groupBy { it.name() }
            .flatMap { (variableName, groupedVariables) ->
                val sourceAssociation = findSources(sourceVariables, variableName, groupedVariables)

                if (groupedVariables.size == 1) {
                    val variable = groupedVariables.first()
                    val association = sourceAssociation[variable]

                    listOf(LocalVariableInfo(variableName, variable, this, association))
                } else {
                    groupedVariables
                        .mapIndexed { variableIndex, variable ->
                            LocalVariableInfo(
                                "${variableName}_$variableIndex",
                                variable,
                                this,
                                sourceAssociation[variable]
                            )
                        }
                }
            }
    }

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

    private val methodSource: CtMethod<*>? by lazy {
        val referenceType = jdiMethod.declaringType()

        val path = CtPathStringBuilder().fromString(
            ".${referenceType.name()}#method[signature=${jdiMethod.name()}(${
                jdiMethod.argumentTypeNames().joinToString(",")
            })]"
        )

        path
            .evaluateOn<CtMethod<*>>(buildParameters.sourceModel.rootPackage)
            .firstOrNull()
    }

    private val body: CtBlock<*>?
        get() = methodSource?.body

    private val sourceVariables: List<CtLocalVariable<*>> by lazy {
        body?.getElements(TypeFilter(CtLocalVariable::class.java)) ?: listOf()
    }

    private val jdiToSource: MultiValuedMap<LocalVariable, CtLocalVariable<*>> by lazy {
        val jdiToSource = ArrayListValuedHashMap<LocalVariable, CtLocalVariable<*>>()

        val sourceVarToScope = sourceVariables.associateWith { sourceVariable ->
            val scopeElements = sourceVariable
                .map(LocalVariableScopeFunction())
                .list<CtElement>()
                .filter { it.position !is NoSourcePosition }
            val sourceMinScopeLine = sourceVariable.position.line
            val sourceMaxScopeLine = scopeElements.maxOf { it.position.endLine }

            sourceMinScopeLine..sourceMaxScopeLine
        }

        for (jdiVariable in jdiVariables) {
            for (sourceVariable in sourceVariables) {
                val jdiMinScopeLine = InternalJDIUtils.getScopeStart(jdiVariable).lineNumber()
                val sourceScope = sourceVarToScope[sourceVariable]!!

                if (jdiMinScopeLine >= 0 && jdiVariable.name() == sourceVariable.simpleName && sourceScope.contains(
                        jdiMinScopeLine
                    )
                ) {
                    jdiToSource.put(jdiVariable, sourceVariable)
                    break
                }
            }
        }

        jdiToSource
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

                if (sourceCandidates.size == 1)
                    sourceCandidates.first()
                else null
            }
        }
    }
}