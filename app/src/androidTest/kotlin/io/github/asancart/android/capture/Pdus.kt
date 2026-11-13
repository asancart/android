package io.github.asancart.android.capture

import java.io.ByteArrayOutputStream

/** 3GPP TS 23.040 SMS-DELIVER PDUs in UCS-2, split into concatenated parts when asked. */
object Pdus {
    fun deliver(sender: String, text: String, maxCharsPerPart: Int = 70, ref: Int = 7): List<ByteArray> {
        require(sender.all { it.isDigit() })
        val parts = if (text.length <= maxCharsPerPart) listOf(text) else text.chunked(minOf(maxCharsPerPart, 67))
        return parts.mapIndexed { i, part -> pdu(sender, part, if (parts.size > 1) Triple(ref, parts.size, i + 1) else null) }
    }

    private fun pdu(sender: String, text: String, concat: Triple<Int, Int, Int>?): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x00) // no SMSC address
        out.write(if (concat != null) 0x44 else 0x04) // SMS-DELIVER, no more messages, UDHI when concatenated
        out.write(sender.length)
        out.write(0x81) // unknown numbering, ISDN plan
        out.write(semiOctets(sender))
        out.write(0x00) // PID
        out.write(0x08) // DCS: UCS-2
        out.write(semiOctets("26111210000000")) // 2026-11-12 10:00:00, timezone 0
        val ud = text.toByteArray(Charsets.UTF_16BE)
        val udh = concat?.let { (r, total, seq) -> byteArrayOf(0x05, 0x00, 0x03, r.toByte(), total.toByte(), seq.toByte()) } ?: ByteArray(0)
        out.write(udh.size + ud.size)
        out.write(udh)
        out.write(ud)
        return out.toByteArray()
    }

    private fun semiOctets(digits: String): ByteArray {
        val d = if (digits.length % 2 == 1) digits + "F" else digits
        return ByteArray(d.length / 2) { i ->
            val lo = d[2 * i].digitToInt(16)
            val hi = d[2 * i + 1].digitToInt(16)
            ((hi shl 4) or lo).toByte()
        }
    }
}
