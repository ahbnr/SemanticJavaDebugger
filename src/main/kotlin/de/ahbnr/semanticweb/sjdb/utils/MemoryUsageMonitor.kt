package de.ahbnr.semanticweb.sjdb.utils

import com.google.common.testing.GcFinalization
import java.lang.management.ManagementFactory


object MemoryUsageMonitor {
    val peakMemoryUse: Long
        get() {
            Object() // ensure there is garbage to collect
            GcFinalization.awaittFullGc()
            return ManagementFactory.getMemoryPoolMXBeans().sumOf { it.peakUsage.used }
        }
}