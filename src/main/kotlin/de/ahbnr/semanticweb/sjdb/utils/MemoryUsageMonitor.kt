package de.ahbnr.semanticweb.sjdb.utils

import java.lang.management.ManagementFactory

object MemoryUsageMonitor {
    val peakMemoryUse: Long
        get() =
            ManagementFactory
                .getMemoryPoolMXBeans().sumOf { it.peakUsage.used }
}