package org.asancart.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.asancart.android.R

/**
 * REQ-BRAND-003: Lalezar for display (titles, big amounts), Vazirmatn for everything else. The font
 * files are the @asancart/tokens woff2 subsets merged per weight into TTF (SIL OFL 1.1; licences in
 * assets/licenses). Lalezar has one weight: never bold it.
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

val Lalezar = FontFamily(Font(R.font.lalezar_regular, FontWeight.Normal))

/** Text roles of the Android design (design/prototype/app.css), sizes in sp at a 360 dp wide phone. */
@Immutable
data class AcTypography(
    /** Welcome title. */
    val display: TextStyle,
    /** Large app bar title on tab screens. */
    val screenTitle: TextStyle,
    /** "آسان‌کارت" next to the mark on Home. */
    val brand: TextStyle,
    /** Small app bar title. */
    val barTitle: TextStyle,
    /** Card and row titles. */
    val title: TextStyle,
    val body: TextStyle,
    val bodyMedium: TextStyle,
    val small: TextStyle,
    val caption: TextStyle,
    /** Section labels above cards. */
    val section: TextStyle,
    val amount: TextStyle,
    val button: TextStyle,
    val nav: TextStyle,
    val tab: TextStyle,
    val stat: TextStyle,
    val statLabel: TextStyle,
    val mono: TextStyle,
)

private val body = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 1.6.em)

val AcType = AcTypography(
    display = TextStyle(fontFamily = Lalezar, fontSize = 30.sp, lineHeight = 1.25.em),
    screenTitle = TextStyle(fontFamily = Lalezar, fontSize = 26.sp, lineHeight = 1.25.em),
    brand = TextStyle(fontFamily = Lalezar, fontSize = 22.sp, lineHeight = 1.2.em),
    barTitle = body.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 1.3.em),
    title = body.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 1.5.em),
    body = body,
    bodyMedium = body.copy(fontWeight = FontWeight.Medium),
    small = body.copy(fontSize = 12.5.sp, lineHeight = 1.55.em),
    caption = body.copy(fontSize = 11.5.sp, lineHeight = 1.45.em),
    section = body.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp, lineHeight = 1.4.em),
    amount = TextStyle(fontFamily = Lalezar, fontSize = 22.sp, lineHeight = 1.15.em),
    button = body.copy(fontWeight = FontWeight.Bold, fontSize = 14.5.sp, lineHeight = 1.2.em),
    nav = body.copy(fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 1.2.em),
    tab = body.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 1.2.em),
    stat = body.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp, lineHeight = 1.4.em),
    statLabel = body.copy(fontSize = 11.sp, lineHeight = 1.4.em),
    mono = body.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp),
)
