package io.github.asancart.android.ui.format

/** Persian digits and separators for UI copy. Card numbers, codes and URLs stay Latin and LTR. */
object PersianFormat {
    private const val DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    private const val GROUP = '\u066C'
    private const val DECIMAL = '\u066B'

    /** Latin digits to Persian; everything else unchanged. */
    fun digits(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '0'..'9') DIGITS[c - '0'] else c)
    }

    /** Grouped by thousands with the Arabic thousands separator: 25000000 -> ۲۵٬۰۰۰٬۰۰۰. */
    fun number(value: Long): String {
        val plain = if (value < 0) (-value).toString() else value.toString()
        val grouped = plain.reversed().chunked(3).joinToString(GROUP.toString()).reversed()
        return (if (value < 0) "-" else "") + digits(grouped)
    }

    /** 0.1.0 -> ۰٫۱٫۰ */
    fun version(name: String): String = digits(name).replace('.', DECIMAL)
}
