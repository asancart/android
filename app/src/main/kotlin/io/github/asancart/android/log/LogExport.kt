package io.github.asancart.android.log

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * REQ-OPS-012: Settings → Logs shares the latest log lines as one text file, for a support Ticket
 * attachment. Nothing is sent automatically. The file holds the newest [MAX_BYTES] of the rolling
 * files, cut at a line start: one `.txt` of at most 1 MB (Q-047, provisional, owner to confirm).
 */
object LogExport {
    const val MAX_BYTES = 1_000_000

    private val NAME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    /** Writes the export into [outDir] (earlier exports are removed) and returns it. */
    fun write(log: FileLog, outDir: File, versionName: String, now: Instant = Instant.now()): File {
        log.flush()
        outDir.mkdirs()
        outDir.listFiles()?.forEach { if (it.name.startsWith("asancart-log-")) it.delete() }
        val header = "Asancart for Android $versionName, log export $now\n".toByteArray()
        val body = newest(log.files().map { it.readBytes() }, MAX_BYTES - header.size)
        val out = File(outDir, "asancart-log-${NAME.format(now)}.txt")
        out.outputStream().use {
            it.write(header)
            it.write(body)
        }
        return out
    }

    /** The newest [max] bytes of [parts] (oldest first), starting at a line. */
    fun newest(parts: List<ByteArray>, max: Int): ByteArray {
        val all = parts.fold(ByteArray(0)) { acc, b -> acc + b }
        if (all.size <= max) return all
        var start = all.size - max
        while (start < all.size && all[start - 1] != '\n'.code.toByte()) start++
        return all.copyOfRange(start, all.size)
    }

    /** The last [n] lines, for the Logs screen. */
    fun tail(log: FileLog, n: Int): List<String> {
        log.flush()
        return log.files().flatMap { it.readLines() }.takeLast(n)
    }
}
