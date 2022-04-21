package de.ahbnr.semanticweb.sjdb.utils

import com.sun.management.GarbageCollectionNotificationInfo
import java.lang.management.ManagementFactory
import javax.management.ListenerNotFoundException
import javax.management.NotificationEmitter
import javax.management.NotificationListener
import javax.management.openmbean.CompositeData
import kotlin.math.max

object MemoryUsageMonitor {
    private fun installListener(listener: NotificationListener) {
        for (bean in ManagementFactory.getGarbageCollectorMXBeans()) {
            (bean as NotificationEmitter).addNotificationListener(listener, null, null)
        }
    }

    private fun uninstallListener(listener: NotificationListener) {
        for (bean in ManagementFactory.getGarbageCollectorMXBeans()) {
            try {
                (bean as NotificationEmitter).removeNotificationListener(listener)
            }

            catch (_: ListenerNotFoundException) {}
        }
    }

    private fun makeListener(): NotificationListener {
        val notifType = GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION

        return NotificationListener { notification, o ->
            if (notification.type == notifType) {
                val gcInfo = GarbageCollectionNotificationInfo
                    .from(notification.userData as CompositeData)
                    .gcInfo

                val usageBefore = gcInfo
                    .memoryUsageBeforeGc
                    .map { it.value.used }
                    .sum()

                val usageAfter = gcInfo
                    .memoryUsageAfterGc
                    .map { it.value.used }
                    .sum()

                peakMemoryUse = max(peakMemoryUse ?: 0, max(usageBefore, usageAfter))
            }
        }
    }

    private var listener: NotificationListener? = null

    @Volatile
    var peakMemoryUse: Long? = null // Unit: bytes
        private set

    @Synchronized
    fun enable() {
        if (listener != null)
            throw java.lang.IllegalStateException("The memory usage monitor is already enabled.")

        makeListener().let {
            listener = it
            installListener(it)
        }
    }

    fun clear() {
        peakMemoryUse = null
    }

    @Synchronized
    fun disable() {
        listener.let {
            if (it == null)
                return

            uninstallListener(it)
            listener = null
        }
    }
}