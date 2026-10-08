package ch.admin.foitt.wallet.platform.locale

import android.os.Build
import ch.admin.foitt.wallet.platform.database.domain.model.DisplayLanguage
import ch.admin.foitt.wallet.platform.sdkVersion.isAtLeast
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.runCatching
import java.util.Locale

object LocaleCompat {
    private const val X_UNKNOWN = "x-unknown"
    private const val X_FALLBACK = "x-fallback"

    private val unknownLocale: Locale = if (isAtLeast(Build.VERSION_CODES.BAKLAVA)) {
        Locale.forLanguageTag(X_UNKNOWN)
    } else {
        Locale.Builder().setLanguageTag(X_UNKNOWN).build()
    }

    fun ofUnknownFormat(unknownFormat: String): Locale {
        val normalizedLocale = unknownFormat.replace("_", "-")
        val cleanedLocale = normalizedLocale
            .replace(DisplayLanguage.UNKNOWN, X_UNKNOWN)
            .replace(DisplayLanguage.FALLBACK, X_FALLBACK)

        return if (isAtLeast(Build.VERSION_CODES.BAKLAVA)) {
            Locale.forLanguageTag(cleanedLocale)
        } else {
            runCatching {
                Locale.Builder().setLanguageTag(cleanedLocale).build()
            }.getOr(unknownLocale)
        }
    }

    fun of(language: String): Locale = if (isAtLeast(Build.VERSION_CODES.BAKLAVA)) {
        Locale.of(language)
    } else {
        if (language.isBlank()) {
            unknownLocale
        } else {
            runCatching {
                Locale.Builder().setLanguage(language).build()
            }.getOr(unknownLocale)
        }
    }

    fun of(language: String, country: String): Locale = if (isAtLeast(Build.VERSION_CODES.BAKLAVA)) {
        Locale.of(language, country)
    } else {
        if (language.isBlank()) {
            unknownLocale
        } else {
            runCatching {
                Locale.Builder().setLanguage(language).setRegion(country).build()
            }.getOr(unknownLocale)
        }
    }
}
