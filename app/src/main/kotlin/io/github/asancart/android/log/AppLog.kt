package io.github.asancart.android.log

import android.util.Log

/**
 * App log (REQ-OPS-010..013). Events are a name plus plain fields, so callers pass ids, template
 * names, reasons and counts, never objects: no SMS text, amounts with account digits, balances or
 * credentials (REQ-OPS-011). Every implementation runs [LogRedactor] on what it writes.
 */
interface AppLog {
    fun info(event: String, vararg fields: Pair<String, Any?>)

    fun warn(event: String, vararg fields: Pair<String, Any?>)

    fun error(event: String, error: Throwable? = null, vararg fields: Pair<String, Any?>)

    companion object {
        /** Logcat in debug builds, nothing otherwise (tests). */
        fun forBuild(debug: Boolean): AppLog = if (debug) LogcatLog else NoLog

        /** The app's log: the rolling files (REQ-OPS-010), plus logcat in debug builds. */
        fun app(file: FileLog, debug: Boolean): AppLog = if (debug) Both(file, LogcatLog) else file
    }
}

private class Both(private val a: AppLog, private val b: AppLog) : AppLog {
    override fun info(event: String, vararg fields: Pair<String, Any?>) {
        a.info(event, *fields)
        b.info(event, *fields)
    }

    override fun warn(event: String, vararg fields: Pair<String, Any?>) {
        a.warn(event, *fields)
        b.warn(event, *fields)
    }

    override fun error(event: String, error: Throwable?, vararg fields: Pair<String, Any?>) {
        a.error(event, error, *fields)
        b.error(event, error, *fields)
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
        val trace = error?.let { "\n" + LogRedactor.redact(it.stackTraceToString()) }.orEmpty()
        Log.e(TAG, line(event, fields) + trace)
    }

    private fun line(event: String, fields: Array<out Pair<String, Any?>>): String =
        fields.joinToString(separator = " ", prefix = LogRedactor.redact(event) + if (fields.isEmpty()) "" else " ") { (k, v) ->
            "$k=${LogRedactor.redactField(k, v)}"
        }
}
