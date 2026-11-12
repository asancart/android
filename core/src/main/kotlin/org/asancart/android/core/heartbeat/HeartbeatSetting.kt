package org.asancart.android.core.heartbeat

/**
 * Per-Profile heartbeat setting (REQ-APP-004). Scheduling and the test button come in T4.4.
 *
 * The interval is kept while the heartbeat is off, so turning it back on restores it.
 */
data class HeartbeatSetting(val enabled: Boolean, val intervalSec: Int) {
    init {
        require(intervalSec in INTERVAL_RANGE) { "intervalSec must be in $INTERVAL_RANGE (REQ-DEV-003)" }
    }

    /** `interval_sec` for REQ-DEV-003: `0` when off, which also tells the server monitoring is off (REQ-DEV-022). */
    val wireIntervalSec: Int get() = if (enabled) intervalSec else 0

    companion object {
        val INTERVAL_RANGE = 60..86_400

        /** On, every 5 minutes, as in the design (my call; the docs set no default). */
        val DEFAULT = HeartbeatSetting(enabled = true, intervalSec = 300)
    }
}
