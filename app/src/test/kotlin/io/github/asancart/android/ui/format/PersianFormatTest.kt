package io.github.asancart.android.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianFormatTest {
    @Test
    fun digitsBecomePersian() {
        assertEquals("کارت ۴۸۲۱", PersianFormat.digits("کارت 4821"))
    }

    @Test
    fun numbersAreGroupedByThousands() {
        assertEquals("۰", PersianFormat.number(0))
        assertEquals("۹۹۹", PersianFormat.number(999))
        assertEquals("۲۵٬۰۰۰٬۰۰۰", PersianFormat.number(25_000_000))
    }

    @Test
    fun versionUsesTheArabicDecimalSeparator() {
        assertEquals("۰٫۱٫۰", PersianFormat.version("0.1.0"))
    }
}
