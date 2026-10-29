package com.example.myapplication.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

internal data class UsageBatch(
    val entries: List<ForegroundEntry>,
    val current: ForegroundEntry?,
    val clockReset: Boolean
)

/** Used by one IO coroutine. Foreground queries never run on the UI thread. */
internal class UsageForegroundReader(context: Context) {
    private val manager = context.getSystemService(UsageStatsManager::class.java)
    private val cursor = UsageEventCursor()
    private var lastRead = 0L

    fun read(): UsageBatch? {
        val now = System.currentTimeMillis()
        val reset = lastRead != 0L && now < lastRead
        if (reset) { cursor.reset(); lastRead = 0L }
        val start = if (reset) now - 1_000 else if (lastRead == 0L) now - 30_000
            else (lastRead - 1_000).coerceAtLeast(now - DAY)
        var entries = query(start, now) ?: return null
        // Keep normal startup small; history is needed only after a restart over a long-open app.
        if (lastRead == 0L && cursor.current == null && entries.isEmpty()) {
            entries = query(now - DAY, now) ?: return null
        }
        lastRead = now
        return UsageBatch(cursor.consume(entries), cursor.current, reset)
    }

    private fun query(start: Long, end: Long): List<ForegroundEntry>? {
        val events = manager.queryEvents(start.coerceAtLeast(0), end + 1) ?: return null
        val event = UsageEvents.Event()
        val entries = ArrayList<ForegroundEntry>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // MOVE_TO_FOREGROUND (SDK 26–28) and ACTIVITY_RESUMED share event value 1.
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && !event.packageName.isNullOrBlank()) {
                entries += ForegroundEntry(event.packageName, event.className.orEmpty(), event.timeStamp)
            }
        }
        return entries
    }

    companion object { private const val DAY = 86_400_000L }
}
