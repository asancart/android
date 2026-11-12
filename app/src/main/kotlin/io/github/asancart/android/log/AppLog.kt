package io.github.asancart.android.log

import android.util.Log

/**
 * Logging seam (REQ-OPS-010..013). T4.6 adds the rolling file log (5 files x 1 MB), log export and
 * ACRA crash reports. Events are a name plus plain fields, so callers pass ids and counts, never
 * objects: no SMS text, amounts with account digits, balances or credentials (REQ-OPS-011).
 */
interface AppLog {
    fun info(event: String, vararg fields: Pair<String, Any?>)

    fun warn(event: String, vararg fields: Pair<String, Any?>)

    fun error(event: String, error: Throwable? = null, vararg fields: Pair<String, Any?>)

    companion object {
        /** Debug builds log to logcat; release builds log nothing until T4.6. */
        fun forBuild(debug: Boolean): AppLog = if (debug) LogcatLog else NoLog
    }
}

private object NoLog : AppLog {
    override fun info(event: String, vararg fields: Pair<String, Any?>) = Unit

    override fun warn(event: String, vararg fields: Pair<String, Any?>) = Unit

    override fun error(event: String, error: Throwable?, vararg fields: Pair<String, Any?>) = Unit
}

private object LogcatLog : AppLog {
    private const val TAG = "Asancart"

    override fun info(event: String, vararg fields: Pair<String, Any?>) {
        Log.i(TAG, line(event, fields))
    }

    override fun warn(event: String, vararg fields: Pair<String, Any?>) {
        Log.w(TAG, line(event, fields))
    }

    override fun error(event: String, error: Throwable?, vararg fields: Pair<String, Any?>) {
        Log.e(TAG, line(event, fields), error)
    }

    private fun line(event: String, fields: Array<out Pair<String, Any?>>): String =
        fields.joinToString(separator = " ", prefix = event + if (fields.isEmpty()) "" else " ") { (k, v) -> "$k=$v" }
}
