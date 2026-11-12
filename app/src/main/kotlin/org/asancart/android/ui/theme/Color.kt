package org.asancart.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Asancart colour tokens (C1 "Saffron & Plum"). Values are copied from asancart/frontend
 * `packages/tokens/tokens.json` (commit c381d66); that file is the source of truth (design/brand.md), so
 * update both together. Screens use these tokens only, never literal colours (REQ-BRAND-001).
 *
 * Brand-coloured text uses [primaryText]; [primary] is for fills with [onPrimary] text (REQ-BRAND-002).
 */
@Immutable
data class AcColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val primary: Color,
    val primaryHover: Color,
    val onPrimary: Color,
    val primaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val ok: Color,
    val warn: Color,
    val bad: Color,
    val info: Color,
    val focus: Color,
    val overlay: Color,
    val shadow: Color,
    val isDark: Boolean,
)

val LightAcColors = AcColors(
    bg = Color(0xFFFAF7F5),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF4EBE5),
    ink = Color(0xFF2A1A12),
    muted = Color(0xFF715D54),
    line = Color(0xFFECDFD7),
    primary = Color(0xFFC84C0A),
    primaryHover = Color(0xFFA63E07),
    onPrimary = Color(0xFFFFFFFF),
    primaryText = Color(0xFFB04208),
    accent = Color(0xFF6B2D84),
    onAccent = Color(0xFFFAF7F5),
    ok = Color(0xFF167246),
    warn = Color(0xFF94590A),
    bad = Color(0xFFC0392B),
    info = Color(0xFF2B6CB0),
    focus = Color(0xFF6B2D84),
    // rgba(42, 26, 18, 0.45)
    overlay = Color(0x732A1A12),
    shadow = Color(0xFF2A1A12),
    isDark = false,
)

val DarkAcColors = AcColors(
    bg = Color(0xFF170F0B),
    surface = Color(0xFF22160F),
    surface2 = Color(0xFF2C1D14),
    ink = Color(0xFFF7EBE3),
    muted = Color(0xFFB39A8D),
    line = Color(0xFF3E2B21),
    primary = Color(0xFFFF8A3D),
    primaryHover = Color(0xFFFF9D5C),
    onPrimary = Color(0xFF2A1103),
    primaryText = Color(0xFFFF8A3D),
    accent = Color(0xFFC79BDD),
    onAccent = Color(0xFF170F0B),
    ok = Color(0xFF4CC38A),
    warn = Color(0xFFE0A84A),
    bad = Color(0xFFF07167),
    info = Color(0xFF6AA8F0),
    focus = Color(0xFFC79BDD),
    // rgba(0, 0, 0, 0.6)
    overlay = Color(0x99000000),
    shadow = Color(0xFF000000),
    isDark = true,
)
