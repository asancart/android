package org.asancart.android

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * The app is Persian only: whatever the phone's language, screens use Persian resources and a
 * right-to-left layout.
 */
object AppLocale {
    val PERSIAN: Locale = Locale.forLanguageTag("fa-IR")

    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(PERSIAN))
        config.setLayoutDirection(PERSIAN)
        return base.createConfigurationContext(config)
    }
}
