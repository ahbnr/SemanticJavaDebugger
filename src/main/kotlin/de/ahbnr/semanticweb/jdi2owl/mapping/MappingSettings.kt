package de.ahbnr.semanticweb.jdi2owl.mapping

class MappingSettings {
    var closeReferenceTypes: Boolean = true

    val additionalExcludedPackages: MutableSet<String> = mutableSetOf()
    val additionalShallowPackages: MutableSet<String> = mutableSetOf()
    val deepFieldsAndVariables: MutableSet<String> = mutableSetOf()

    var limitSdk: Boolean = false
        set(value) {
            if (field != value) {
                _allExcludedPackages = null
                _allShallowPackages = null
            }

            field = value
        }

    val excludedSdkPackages
        get() = if (limitSdk) setOf(
            "sun",
            "jdk",
            "java.security",
            "java.lang.reflect",
            "java.lang.ref",
            "java.lang.module",
            "java.lang.invoke",
            "java.lang.annotation",
            "java.lang.module",
            "java.lang.reflect",
            "java.net",
            "java.nio",
            "java.util.concurrent",
        ) else emptySet()
    val shallowSdkPackages
        get() = if (limitSdk) setOf("java") else emptySet()

    private var _allExcludedPackages: Set<String>? = null
    val allExcludedPackages: Set<String>
        get() = _allExcludedPackages.let {
            if (it == null) {
                val newSet = mutableSetOf<String>().apply {
                    addAll(additionalExcludedPackages)
                    addAll(excludedSdkPackages)
                }
                _allExcludedPackages = newSet
                newSet
            } else it
        }
    private var _allShallowPackages: Set<String>? = null
    val allShallowPackages: Set<String>
        get() = _allShallowPackages.let {
            if (it == null) {
                val newSet = mutableSetOf<String>().apply {
                    addAll(additionalShallowPackages)
                    addAll(shallowSdkPackages)
                }
                _allShallowPackages = newSet
                newSet
            } else it
        }
}