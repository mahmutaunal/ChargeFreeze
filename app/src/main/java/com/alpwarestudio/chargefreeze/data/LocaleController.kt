package com.alpwarestudio.chargefreeze.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleController {
    fun wrap(context: Context): Context {
        val mode = AppPreferences(context).languageMode
        if (mode == LanguageMode.SYSTEM) {
            context.resources.configuration.locales.get(0)?.let(Locale::setDefault)
            return context
        }

        val locale = Locale.forLanguageTag(mode.value)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }
}
