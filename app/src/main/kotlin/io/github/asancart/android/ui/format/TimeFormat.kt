package io.github.asancart.android.ui.format

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import io.github.asancart.android.R
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/** Jalali dates in Persian digits through the Persian calendar of `android.icu` (REQ-APP-042: minSdk 24). */
object TimeFormat {
    private val PERSIAN = ULocale("fa_IR@calendar=persian")

    /** «امروز ۱۴:۲۱», «دیروز ۱۸:۳۰», or «۲۰ آبان ۱۴:۲۱». */
    fun smsTime(context: Context, at: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val day = at.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val date = Date.from(at)
        val time = format("HH:mm", date)
        return when (day) {
            today -> context.getString(R.string.time_today, time)
            today.minusDays(1) -> context.getString(R.string.time_yesterday, time)
            else -> format("d MMMM HH:mm", date)
        }
    }

    private fun format(pattern: String, date: Date): String = SimpleDateFormat(pattern, PERSIAN).format(date)
}
