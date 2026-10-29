package com.example.myapplication.service

/** Memory-only authentication grants, measured against monotonic elapsed time. */
class RelockPolicy(private val now: () -> Long) {
    enum class Mode { AFTER_QUITTING, AFTER_SCREEN_OFF }
    private val grants = mutableMapOf<String, Long?>()
    private var foreground: String? = null
    var mode = Mode.AFTER_QUITTING
        private set
    var delayMillis = 0L
        private set

    fun configure(mode: Mode, delayMillis: Long) {
        if (this.mode != mode || this.delayMillis != delayMillis) clear()
        this.mode = mode
        this.delayMillis = delayMillis.coerceAtLeast(0)
    }
    fun authenticate(packageName: String) {
        grants[packageName] = null
        foreground = packageName
    }
    fun onForeground(packageName: String?, observedAt: Long = now()) {
        if (foreground == packageName) return
        if (mode == Mode.AFTER_QUITTING) {
            foreground?.let { previous ->
                if (grants.containsKey(previous)) grants[previous] = observedAt + delayMillis
            }
        }
        foreground = packageName
        if (mode == Mode.AFTER_QUITTING && packageName != null && isAuthenticatedAt(packageName, observedAt)) {
            grants[packageName] = null
        }
    }
    fun onScreenOff() {
        if (mode == Mode.AFTER_SCREEN_OFF) {
            val expiry = now() + delayMillis
            grants.keys.toList().forEach { grants[it] = expiry }
        } else onForeground(null)
        foreground = null
    }
    fun isAuthenticated(packageName: String): Boolean = isAuthenticatedAt(packageName, now())
    private fun isAuthenticatedAt(packageName: String, at: Long): Boolean {
        if (!grants.containsKey(packageName)) return false
        val expiry = grants[packageName]
        if (expiry != null && at >= expiry) { grants.remove(packageName); return false }
        return true
    }
    fun retainPackages(packages: Set<String>) { grants.keys.retainAll(packages) }
    fun nextExpiry(): Long? = grants.values.filterNotNull().minOrNull()
    fun expireGrants() { grants.entries.removeAll { (_, expiry) -> expiry != null && now() >= expiry } }
    fun clear() { grants.clear(); foreground = null }
}
