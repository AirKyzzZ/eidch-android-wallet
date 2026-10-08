package ch.admin.foitt.wallet.platform.locale

import android.os.Build
import ch.admin.foitt.wallet.platform.sdkVersion.isAtLeast
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.Locale

class LocaleCompatTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @ParameterizedTest
    @MethodSource("validUnknownFormatInput")
    fun `ofUnknownFormat parses valid input`(input: Pair<String, Locale>) {
        mockSdkVersion(isBaklavaOrAbove = true)

        val locale = LocaleCompat.ofUnknownFormat(input.first)

        assertEquals(input.second, locale)
    }

    @ParameterizedTest
    @MethodSource("validUnknownFormatInput")
    fun `ofUnknownFormat parses valid input on legacy sdks`(input: Pair<String, Locale>) {
        mockSdkVersion(isBaklavaOrAbove = false)

        val locale = LocaleCompat.ofUnknownFormat(input.first)

        assertEquals(input.second, locale)
    }

    @ParameterizedTest
    @MethodSource("invalidUnknownFormatInput")
    fun `ofUnknownFormat maps invalid input to the root locale`(input: String) {
        mockSdkVersion(isBaklavaOrAbove = true)

        val locale = LocaleCompat.ofUnknownFormat(input)

        assertEquals(Locale.ROOT, locale)
    }

    @ParameterizedTest
    @MethodSource("invalidUnknownFormatInput")
    fun `ofUnknownFormat maps invalid input to the unknown locale on legacy sdks`(input: String) {
        mockSdkVersion(isBaklavaOrAbove = false)

        val locale = LocaleCompat.ofUnknownFormat(input)

        assertEquals(unknownLocale, locale)
    }

    @ParameterizedTest
    @MethodSource("validLanguageInput")
    fun `of parses valid languages`(input: Pair<String, Locale>) {
        mockSdkVersion(isBaklavaOrAbove = true)

        val locale = LocaleCompat.of(input.first)

        assertEquals(input.second, locale)
    }

    @ParameterizedTest
    @MethodSource("validLanguageInput")
    fun `of parses valid languages on legacy sdks`(input: Pair<String, Locale>) {
        mockSdkVersion(isBaklavaOrAbove = false)

        val locale = LocaleCompat.of(input.first)

        assertEquals(input.second, locale)
    }

    @ParameterizedTest
    @MethodSource("invalidLanguageInput")
    fun `of keeps invalid languages`(input: String) {
        mockSdkVersion(isBaklavaOrAbove = true)

        val locale = LocaleCompat.of(input)

        assertEquals(input.lowercase(), locale.language)
    }

    @ParameterizedTest
    @MethodSource("invalidLanguageInput")
    fun `of maps invalid languages to the unknown locale on legacy sdks`(input: String) {
        mockSdkVersion(isBaklavaOrAbove = false)

        val locale = LocaleCompat.of(input)

        assertEquals(unknownLocale.toLanguageTag(), locale.toLanguageTag())
    }

    @ParameterizedTest
    @MethodSource("validLanguageAndCountryInput")
    fun `of parses valid languages and countries`(input: Pair<Pair<String, String>, Locale>) {
        mockSdkVersion(isBaklavaOrAbove = true)

        val locale = LocaleCompat.of(input.first.first, input.first.second)

        assertEquals(input.second, locale)
    }

    @ParameterizedTest
    @MethodSource("validLanguageAndCountryInput")
    fun `of parses valid languages and countries on legacy sdks`(input: Pair<Pair<String, String>, Locale>) {
        mockSdkVersion(isBaklavaOrAbove = false)

        val locale = LocaleCompat.of(input.first.first, input.first.second)

        assertEquals(input.second, locale)
    }

    @ParameterizedTest
    @MethodSource("invalidLanguageAndCountryInput")
    fun `of keeps invalid languages and countries`(input: Pair<String, String>) {
        mockSdkVersion(isBaklavaOrAbove = true)

        val locale = LocaleCompat.of(input.first, input.second)

        assertEquals(input.first.lowercase(), locale.language)
        assertEquals(input.second, locale.country)
    }

    @ParameterizedTest
    @MethodSource("invalidLanguageAndCountryInput")
    fun `of maps invalid languages and countries to the unknown locale on legacy sdks`(input: Pair<String, String>) {
        mockSdkVersion(isBaklavaOrAbove = false)

        val locale = LocaleCompat.of(input.first, input.second)

        assertEquals(unknownLocale.toLanguageTag(), locale.toLanguageTag())
    }

    private fun mockSdkVersion(isBaklavaOrAbove: Boolean) {
        mockkStatic(::isAtLeast)
        every { isAtLeast(Build.VERSION_CODES.BAKLAVA) } returns isBaklavaOrAbove
    }

    companion object {
        private val unknownLocale: Locale = Locale.forLanguageTag("x-unknown")
        private val fallbackLocale: Locale = Locale.forLanguageTag("x-fallback")
        private val swissGerman: Locale = Locale.forLanguageTag("de-CH")

        @JvmStatic
        fun validUnknownFormatInput() = listOf(
            "en" to Locale.ENGLISH,
            "de" to Locale.GERMAN,
            "en-US" to Locale.US,
            "en_US" to Locale.US,
            "unknown" to unknownLocale,
            "fallback" to fallbackLocale,
        )

        @JvmStatic
        fun invalidUnknownFormatInput() = listOf(
            "",
            " ",
            "-",
            "_",
            "--",
            "something",
            "1234",
            "-de",
        )

        @JvmStatic
        fun validLanguageInput() = listOf(
            "de" to Locale.GERMAN,
            "en" to Locale.ENGLISH,
            "DE" to Locale.GERMAN,
        )

        @JvmStatic
        fun invalidLanguageInput() = listOf(
            "",
            " ",
            "-",
            "_",
            "--",
            "1234",
            "de-",
            "-de",
            "de--CH",
            "something",
            "x-unknown",
        )

        @JvmStatic
        fun validLanguageAndCountryInput() = listOf(
            ("de" to "CH") to swissGerman,
            ("en" to "US") to Locale.US,
            ("de" to "") to Locale.GERMAN,
        )

        @JvmStatic
        fun invalidLanguageAndCountryInput() = listOf(
            "" to "",
            "de" to "XYZ",
            "something" to "CH",
        )
    }
}
