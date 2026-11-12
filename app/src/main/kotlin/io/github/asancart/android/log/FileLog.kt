package io.github.asancart.android.log

import java.io.File
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * REQ-OPS-010: local rotating log files in app-private storage, [maxFiles] × [maxBytes]
 * (5 × 1 MB): `app.log` is written, `app.1.log` … `app.4.log` are older; the oldest is deleted
 * first. One JSON object per line (REQ-OPS-001). Lines are redacted (REQ-OPS-003) before they
 * reach the disk. Nothing is sent anywhere (REQ-OPS-012).
 */
class FileLog(
    val dir: File,
    private val env: String,
    private val maxFiles: Int = 5,
    private val maxBytes: Long = 1_000_000,
    private val clock: () -> Instant = Instant::now,
) : AppLog {
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "asancart-log").apply { isDaemon = true } }
    private val current = File(dir, "app.log")

    override fun info(event: String, vararg fields: Pair<String, Any?>) = enqueue("INFO", event, null, fields)

    override fun warn(event: String, vararg fields: Pair<String, Any?>) = enqueue("WARN", event, null, fields)

    override fun error(event: String, error: Throwable?, vararg fields: Pair<String, Any?>) = enqueue("ERROR", event, error, fields)

    /** Writes now, on the calling thread: for the crash handler, which runs just before the process dies. */
    fun errorNow(event: String, error: Throwable?, vararg fields: Pair<String, Any?>) {
        val line = line("ERROR", event, error, fields)
        synchronized(this) { append(line) }
    }

    /** Files from oldest to newest. */
    fun files(): List<File> = (maxFiles - 1 downTo 1).map { File(dir, "app.$it.log") }.filter { it.exists() } + listOf(current).filter { it.exists() }

    /** Waits for queued lines (tests, export). */
    fun flush() {
        writer.submit {}.get(5, TimeUnit.SECONDS)
    }

    private fun enqueue(level: String, event: String, error: Throwable?, fields: Array<out Pair<String, Any?>>) {
        val line = line(level, event, error, fields)
        writer.execute { synchronized(this) { append(line) } }
    }

    private fun line(level: String, event: String, error: Throwable?, fields: Array<out Pair<String, Any?>>): String {
        val sb = StringBuilder(160)
        sb.append("{\"time\":").append(json(clock().toString()))
        sb.append(",\"level\":").append(json(level))
        sb.append(",\"app\":\"android\",\"env\":").append(json(env))
        sb.append(",\"msg\":").append(json(LogRedactor.redact(event)))
        for ((k, v) in fields) sb.append(',').append(json(LogRedactor.redact(k))).append(':').append(json(LogRedactor.redactField(k, v)))
        if (error != null) sb.append(",\"error\":").append(json(LogRedactor.redact(error.stackTraceToString().take(MAX_TRACE))))
        sb.append("}\n")
        return sb.toString()
    }

    private fun append(line: String) {
        try {
            if (!dir.exists()) dir.mkdirs()
            if (current.exists() && current.length() + line.length > maxBytes) rotate()
            current.appendText(line)
        } catch (_: Exception) {
            // Logging never takes the app down.
        }
    }

    private fun rotate() {
        File(dir, "app.${maxFiles - 1}.log").delete()
        for (i in maxFiles - 2 downTo 1) {
            val f = File(dir, "app.$i.log")
            if (f.exists()) f.renameTo(File(dir, "app.${i + 1}.log"))
        }
        current.renameTo(File(dir, "app.1.log"))
    }

    companion object {
        private const val MAX_TRACE = 16_000

        fun json(s: String): String {
            val sb = StringBuilder(s.length + 2).append('"')
            for (c in s) {
                when {
                    c == '"' -> sb.append("\\\"")
                    c == '\\' -> sb.append("\\\\")
                    c == '\n' -> sb.append("\\n")
                    c == '\r' -> sb.append("\\r")
                    c == '\t' -> sb.append("\\t")
                    c < ' ' -> sb.append("\\u%04x".format(c.code))
                    else -> sb.append(c)
                }
            }
            return sb.append('"').toString()
        }
    }
}
