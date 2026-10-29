package com.example.myapplication.service

internal data class ForegroundEntry(val packageName: String, val className: String, val timestamp: Long)

/** Deduplicates overlapping queries without losing distinct entries at the same millisecond. */
internal class UsageEventCursor {
    private var timestamp = Long.MIN_VALUE
    private var countAtTimestamp = 0
    var current: ForegroundEntry? = null
        private set

    fun consume(entries: List<ForegroundEntry>): List<ForegroundEntry> {
        var seenAtBoundary = 0
        val fresh = ArrayList<ForegroundEntry>()
        for (entry in entries) {
            if (entry.timestamp < timestamp) continue
            if (entry.timestamp > timestamp) {
                timestamp = entry.timestamp
                countAtTimestamp = 0
                seenAtBoundary = 0
            }
            seenAtBoundary++
            if (seenAtBoundary <= countAtTimestamp) continue
            countAtTimestamp = seenAtBoundary
            current = entry
            fresh += entry
        }
        return fresh
    }

    fun reset() {
        timestamp = Long.MIN_VALUE
        countAtTimestamp = 0
        // Preserve the last known owner across a wall-clock adjustment with no new resume event.
    }
}
