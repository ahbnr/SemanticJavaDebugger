package de.ahbnr.semanticweb.sjdb.utils

import com.sun.management.GarbageCollectionNotificationInfo
import java.lang.management.ManagementFactory
import java.util.*
import javax.management.ListenerNotFoundException
import javax.management.NotificationEmitter
import javax.management.NotificationListener
import javax.management.openmbean.CompositeData
import kotlin.concurrent.schedule
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

                _gcPeakMemoryUse = max(_gcPeakMemoryUse, max(usageBefore, usageAfter))
            }
        }
    }

    private fun makeTimer(): Timer {
        val memoryMxBean = ManagementFactory.getMemoryMXBean()
        val timer = Timer(true)

        timer.schedule(delay = 0, period = 25) {
            _timerPeakMemoryUse = max(
                _timerPeakMemoryUse,
                memoryMxBean.heapMemoryUsage.used + memoryMxBean.nonHeapMemoryUsage.used
            )
        }

        return timer
    }

    private var listener: NotificationListener? = null

    private var timer: Timer? = null

    @Volatile
    private var _gcPeakMemoryUse: Long = 0 // Unit: bytes

    @Volatile
    private var _timerPeakMemoryUse: Long = 0
        get() {
            if (field == 0L) {
                val memoryMxBean = ManagementFactory.getMemoryMXBean()
                field = memoryMxBean.heapMemoryUsage.used + memoryMxBean.nonHeapMemoryUsage.used
            }

            return field
        }

    private val _poolsPeakMemoryUse: Long
        get() = ManagementFactory.getMemoryPoolMXBeans().sumOf { it.peakUsage.used }

    val peakMemoryUse: Long
        get() {
            if (listener == null || timer == null)
                throw java.lang.IllegalStateException("Memory monitor is not enabled. Run SJDB with --monitor-memory.")

            // None of these measurements always seems to be fully up-to-date.
            // Hence, we combine all of them
            return max(
                _gcPeakMemoryUse,
                max(_poolsPeakMemoryUse, _timerPeakMemoryUse)
            )
        }

    @Synchronized
    fun enable() {
        if (listener != null || timer != null)
            throw java.lang.IllegalStateException("The memory usage monitor is already enabled.")

        makeListener().let {
            listener = it
            installListener(it)
        }

        timer = makeTimer()
    }

    @Synchronized
    fun disable() {
        listener?.let {
            uninstallListener(it)
            listener = null
        }

        timer?.let {
            it.cancel()
            timer = null
        }
    }
}