package app.gyrolet.mpvrx.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {
  const val PREF_NAME = "app_locale_prefs"
  const val KEY_APP_LANGUAGE = "app_language"

  val supportedLanguages: List<SupportedLanguage> = listOf(
    SupportedLanguage("", "System", "System"),
    SupportedLanguage("de", "Deutsch", "Deutsch"),
    SupportedLanguage("es", "Español", "Español"),
    SupportedLanguage("fr", "Français", "Français"),
    SupportedLanguage("hi", "हिन्दी", "हिन्दी"),
    SupportedLanguage("it", "Italiano", "Italiano"),
    SupportedLanguage("ja", "日本語", "日本語"),
    SupportedLanguage("ko", "한국어", "한국어"),
    SupportedLanguage("pl", "Polski", "Polski"),
    SupportedLanguage("pt", "Português", "Português"),
    SupportedLanguage("pt-rBR", "Português (Brasil)", "Português (Brasil)"),
    SupportedLanguage("ru", "Русский", "Русский"),
    SupportedLanguage("tr", "Türkçe", "Türkçe"),
    SupportedLanguage("zh-rCN", "简体中文", "简体中文"),
  )

  data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
  )

  fun getPersistedLocale(context: Context): Locale {
    val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val langCode = prefs.getString(KEY_APP_LANGUAGE, "") ?: ""
    return localeFromCode(langCode)
  }

  fun persistLocale(context: Context, languageCode: String) {
    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_APP_LANGUAGE, languageCode)
      .apply()
  }

  fun localeFromCode(code: String): Locale {
    if (code.isBlank()) return Locale.getDefault()
    val parts = code.split("-")
    return if (parts.size == 2) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build()
      } else {
        Locale(parts[0], parts[1])
      }
    } else {
      Locale(code)
    }
  }

  fun wrapContext(context: Context): ContextWrapper {
    val locale = getPersistedLocale(context)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      config.setLocales(android.os.LocaleList(locale))
    }
    val localizedContext = context.createConfigurationContext(config)
    return object : ContextWrapper(localizedContext) {}
  }

  fun wrapContextWithFontScale(context: Context, fontScale: Float): ContextWrapper {
    val locale = getPersistedLocale(context)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    config.fontScale = fontScale
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      config.setLocales(android.os.LocaleList(locale))
    }
    val localizedContext = context.createConfigurationContext(config)
    return object : ContextWrapper(localizedContext) {}
  }
}
