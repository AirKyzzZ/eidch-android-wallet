package ch.admin.foitt.wallet.platform.ssi.data.repository

import android.webkit.URLUtil
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.AnyCredentialConfiguration
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.CredentialFormat
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.CredentialMetadata
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.CredentialRequestEncryption
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.CredentialResponseEncryption
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.IssuerCredentialInfo
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.Logo
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.OidCredentialDisplay
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.OidIssuerDisplay
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.VcSdJwtCredentialConfiguration
import ch.admin.foitt.openid4vc.domain.model.jwk.Jwks
import ch.admin.foitt.wallet.platform.database.data.dao.CredentialDao
import ch.admin.foitt.wallet.platform.database.data.dao.CredentialDisplayDao
import ch.admin.foitt.wallet.platform.database.data.dao.CredentialIssuerDisplayDao
import ch.admin.foitt.wallet.platform.database.data.dao.DaoProvider
import ch.admin.foitt.wallet.platform.database.data.dao.ImageEntityDao
import ch.admin.foitt.wallet.platform.database.data.dao.RawCredentialDataDao
import ch.admin.foitt.wallet.platform.database.domain.model.Credential
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialDisplay
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialDisplayEntity
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialIssuerDisplay
import ch.admin.foitt.wallet.platform.database.domain.model.CredentialIssuerDisplayEntity
import ch.admin.foitt.wallet.platform.database.domain.model.DisplayLanguage
import ch.admin.foitt.wallet.platform.database.domain.model.RawCredentialData
import ch.admin.foitt.wallet.platform.database.domain.usecase.RunInTransaction
import ch.admin.foitt.wallet.platform.keybindingMatching.domain.usecase.MatchKeyBindingToPayloadCnf
import ch.admin.foitt.wallet.platform.locale.domain.usecase.GetCurrentAppLocale
import ch.admin.foitt.wallet.platform.locale.domain.usecase.implementation.GetLocalizedCredentialInformationDisplayImpl
import ch.admin.foitt.wallet.platform.oca.domain.model.OcaBundle
import ch.admin.foitt.wallet.platform.oca.domain.model.OcaCredentialData
import ch.admin.foitt.wallet.platform.oca.domain.model.OcaError
import ch.admin.foitt.wallet.platform.oca.domain.usecase.OcaBundler
import ch.admin.foitt.wallet.platform.theme.domain.model.Theme
import ch.admin.foitt.wallet.platform.utils.SafeJson
import ch.admin.foitt.wallet.platform.utils.compress
import ch.admin.foitt.wallet.platform.utils.domain.usecase.GetImageDataFromUri
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Round-trip tests for [CredentialOfferRepositoryImpl.refreshCachedImages]:
 * the display rows are seeded the way migration 30→31 leaves them (every hash null) and
 * the refresh must resolve each row back to the URI its logo originally came from.
 */
class CredentialOfferRepositoryImplTest {

    companion object {
        private const val CREDENTIAL_ID = 100L
        private const val EN_LOGO_URI = "data:image/png;base64,logo-en"
        private const val LIGHT_LOGO_URI = "data:image/png;base64,logo-light"
        private const val DARK_LOGO_URI = "data:image/png;base64,logo-dark"
        private const val METADATA_LOGO_URI = "data:image/png;base64,logo-metadata"
    }

    @MockK(relaxed = true)
    private lateinit var mockDaoProvider: DaoProvider

    @MockK
    private lateinit var mockCredentialDao: CredentialDao

    @MockK
    private lateinit var mockCredentialDisplayDao: CredentialDisplayDao

    @MockK
    private lateinit var mockCredentialIssuerDisplayDao: CredentialIssuerDisplayDao

    @MockK
    private lateinit var mockRawCredentialDataDao: RawCredentialDataDao

    @MockK
    private lateinit var mockImageEntityDao: ImageEntityDao

    @MockK
    private lateinit var mockOcaBundler: OcaBundler

    @MockK(relaxed = true)
    private lateinit var mockMatchKeyBindingToPayloadCnf: MatchKeyBindingToPayloadCnf

    private val safeJson = SafeJson(
        // Same configuration as the "JsonSerializer" in UtilModule
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
    )

    private val getCurrentAppLocale = object : GetCurrentAppLocale {
        override fun invoke(): Locale = Locale.GERMAN
    }

    private val runInTransaction = object : RunInTransaction {
        override suspend fun <V> invoke(block: suspend () -> V): V? = block()
    }

    private var uriToImageData = emptyMap<String, ByteArray>()

    private val getImageDataFromUri = GetImageDataFromUri { uri ->
        uri?.let { uriToImageData[it] }
    }

    private lateinit var repository: CredentialOfferRepositoryImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        // The metadata decode validates URLs via URLUtil, which is a stub on the JVM
        mockkStatic(URLUtil::class)
        every { URLUtil.isHttpsUrl(any()) } returns true

        every { mockDaoProvider.credentialDaoFlow } returns MutableStateFlow(mockCredentialDao)
        every { mockDaoProvider.credentialDisplayDaoFlow } returns MutableStateFlow(mockCredentialDisplayDao)
        every {
            mockDaoProvider.credentialIssuerDisplayDaoFlow
        } returns MutableStateFlow(mockCredentialIssuerDisplayDao)
        every { mockDaoProvider.rawCredentialDataDao } returns MutableStateFlow(mockRawCredentialDataDao)
        every { mockDaoProvider.imageEntityDao } returns MutableStateFlow(mockImageEntityDao)

        every { mockCredentialIssuerDisplayDao.updateImageHash(any(), any()) } returns Unit
        every { mockCredentialDisplayDao.updateLogoHash(any(), any()) } returns Unit
        every { mockImageEntityDao.insertAll(any()) } returns Unit
        every { mockCredentialDao.getById(CREDENTIAL_ID) } returns credential(selectedConfigurationId = null)

        repository = CredentialOfferRepositoryImpl(
            daoProvider = mockDaoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            getLocalizedCredentialInformationDisplay = GetLocalizedCredentialInformationDisplayImpl(
                getCurrentAppLocale = getCurrentAppLocale
            ),
            runInTransaction = runInTransaction,
            matchKeyBindingToPayloadCnf = mockMatchKeyBindingToPayloadCnf,
            getImageDataFromUri = getImageDataFromUri,
            safeJson = safeJson,
            ocaBundler = mockOcaBundler,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `refreshCachedImages resolves trusted issuer rows fuzzily and skips the synthetic fallback row`() =
        runTest {
            // Rows come from the trust statement locales, the metadata publishes one "en" logo
            seedIssuerRows(
                idToLocale = mapOf(1L to "de", 2L to "fr", 3L to "it", 4L to "en", 5L to DisplayLanguage.FALLBACK)
            )
            seedCredentialRows(emptyMap())
            seedRawCredentialData(
                issuerDisplays = listOf(OidIssuerDisplay(locale = "en", logo = Logo(uri = EN_LOGO_URI))),
                rawOcaBundle = null,
            )
            uriToImageData = mapOf(EN_LOGO_URI to "logo-en".toByteArray())

            val result = repository.refreshCachedImages(CREDENTIAL_ID)

            assertTrue(result.isOk, "refreshCachedImages should succeed: $result")
            val expectedHash = sha256("logo-en".toByteArray())
            // Every real row resolves to the "en" logo; the synthetic FALLBACK row gets none
            verify { mockCredentialIssuerDisplayDao.updateImageHash(1L, expectedHash) }
            verify { mockCredentialIssuerDisplayDao.updateImageHash(2L, expectedHash) }
            verify { mockCredentialIssuerDisplayDao.updateImageHash(3L, expectedHash) }
            verify { mockCredentialIssuerDisplayDao.updateImageHash(4L, expectedHash) }
            verify(exactly = 0) { mockCredentialIssuerDisplayDao.updateImageHash(5L, any()) }
        }

    @Test
    fun `refreshCachedImages resolves credential rows by locale and theme`() = runTest {
        // One locale with light and dark branding: two rows differing only by theme
        seedIssuerRows(mapOf(1L to "en"))
        seedCredentialRows(
            idToLocaleAndTheme = mapOf(
                1L to ("de" to Theme.LIGHT.value),
                2L to ("de" to Theme.DARK.value),
            )
        )
        seedRawCredentialData(
            issuerDisplays = listOf(OidIssuerDisplay(locale = "en", logo = Logo(uri = EN_LOGO_URI))),
            rawOcaBundle = "bundle".toByteArray().compress(),
        )
        coEvery {
            mockOcaBundler(any())
        } returns Ok(
            OcaBundle(
                captureBases = emptyList(),
                overlays = emptyList(),
                ocaCredentialData = listOf(
                    OcaCredentialData(
                        captureBaseDigest = "digest",
                        locale = "de",
                        theme = Theme.LIGHT.value,
                        name = "credential",
                        description = null,
                        logoData = LIGHT_LOGO_URI,
                        backgroundColor = null,
                    ),
                    OcaCredentialData(
                        captureBaseDigest = "digest",
                        locale = "de",
                        theme = Theme.DARK.value,
                        name = "credential",
                        description = null,
                        logoData = DARK_LOGO_URI,
                        backgroundColor = null,
                    ),
                ),
            )
        )
        every { mockCredentialDao.getById(CREDENTIAL_ID) } returns credential(selectedConfigurationId = null)
        uriToImageData = mapOf(
            EN_LOGO_URI to "logo-en".toByteArray(),
            LIGHT_LOGO_URI to "logo-light".toByteArray(),
            DARK_LOGO_URI to "logo-dark".toByteArray(),
        )

        val result = repository.refreshCachedImages(CREDENTIAL_ID)

        assertTrue(result.isOk, "refreshCachedImages should succeed: $result")
        // The dark row must get the dark logo, not the first record's (locale-only match)
        verify { mockCredentialDisplayDao.updateLogoHash(1L, sha256("logo-light".toByteArray())) }
        verify { mockCredentialDisplayDao.updateLogoHash(2L, sha256("logo-dark".toByteArray())) }
    }

    @Test
    fun `refreshCachedImages falls back to metadata images when the OCA bundle is unreadable`() = runTest {
        // An unparseable bundle means the save path generated the displays from the metadata,
        // so the backfill has to resolve against that same metadata
        seedIssuerRows(mapOf(1L to "en"))
        seedCredentialRows(mapOf(1L to ("de" to Theme.LIGHT.value)))
        seedRawCredentialData(
            issuerDisplays = listOf(OidIssuerDisplay(locale = "en", logo = Logo(uri = EN_LOGO_URI))),
            rawOcaBundle = "unreadable bundle".toByteArray().compress(),
            configurations = listOf(
                VcSdJwtCredentialConfiguration(
                    identifier = "cfg",
                    vct = "vct",
                    credentialSigningAlgValuesSupported = emptyList(),
                    vctIntegrity = null,
                    vctMetadataUri = null,
                    vctMetadataUriIntegrity = null,
                    credentialMetadata = CredentialMetadata(
                        display = listOf(
                            OidCredentialDisplay(locale = "de", name = "credential", logo = Logo(uri = METADATA_LOGO_URI))
                        )
                    ),
                )
            ),
        )
        coEvery { mockOcaBundler(any()) } returns Err(OcaError.InvalidJsonObject)
        every { mockCredentialDao.getById(CREDENTIAL_ID) } returns credential(selectedConfigurationId = "cfg")
        uriToImageData = mapOf(
            EN_LOGO_URI to "logo-en".toByteArray(),
            METADATA_LOGO_URI to "logo-metadata".toByteArray(),
        )

        val result = repository.refreshCachedImages(CREDENTIAL_ID)

        assertTrue(result.isOk, "refreshCachedImages should succeed: $result")
        // The metadata logo, matching the source the save path actually used
        verify { mockCredentialDisplayDao.updateLogoHash(1L, sha256("logo-metadata".toByteArray())) }
        verify { mockCredentialIssuerDisplayDao.updateImageHash(1L, sha256("logo-en".toByteArray())) }
    }

    // region seeding — the state migration 30→31 leaves behind: every display hash null

    private fun seedIssuerRows(idToLocale: Map<Long, String>) {
        val rows = idToLocale.map { (id, locale) ->
            CredentialIssuerDisplay(
                entity = CredentialIssuerDisplayEntity(
                    id = id,
                    credentialId = CREDENTIAL_ID,
                    imageHash = null,
                    locale = locale,
                ),
                image = null,
            )
        }
        every { mockCredentialIssuerDisplayDao.getCredentialIssuerDisplaysById(CREDENTIAL_ID) } returns rows
    }

    private fun seedCredentialRows(idToLocaleAndTheme: Map<Long, Pair<String, String>>) {
        val rows = idToLocaleAndTheme.map { (id, localeAndTheme) ->
            CredentialDisplay(
                entity = CredentialDisplayEntity(
                    id = id,
                    credentialId = CREDENTIAL_ID,
                    locale = localeAndTheme.first,
                    logoHash = null,
                    theme = localeAndTheme.second,
                ),
                logo = null,
            )
        }
        every { mockCredentialDisplayDao.getByCredentialId(CREDENTIAL_ID) } returns rows
    }

    private fun seedRawCredentialData(
        issuerDisplays: List<OidIssuerDisplay>,
        rawOcaBundle: ByteArray?,
        configurations: List<AnyCredentialConfiguration> = emptyList(),
    ) {
        val issuerInfo = IssuerCredentialInfo(
            credentialEndpoint = URL("https://example.com/credential"),
            nonceEndpoint = URL("https://example.com/nonce"),
            credentialIssuer = URL("https://example.com"),
            identityTrustStatement = null,
            credentialRequestEncryption = CredentialRequestEncryption(
                jwks = Jwks(emptyList()),
                encValuesSupported = emptyList(),
                zipValuesSupported = null,
                encryptionRequired = false,
            ),
            credentialResponseEncryption = CredentialResponseEncryption(
                algValuesSupported = emptyList(),
                encValuesSupported = emptyList(),
                zipValuesSupported = null,
                encryptionRequired = false,
            ),
            credentialConfigurations = configurations,
            display = issuerDisplays,
        )
        val rawCredentialData = RawCredentialData(
            credentialId = CREDENTIAL_ID,
            rawOcaBundle = rawOcaBundle,
            rawOIDMetadata = rawMetadataJson(issuerInfo).toByteArray().compress(),
        )
        every {
            mockRawCredentialDataDao.getRawCredentialDataByCredentialId(CREDENTIAL_ID)
        } returns listOf(rawCredentialData)
    }

    /**
     * Reshapes the encoded `credential_configurations_supported` list into the object keyed by
     * identifier that real issuers publish, which is what the serializer accepts when decoding.
     */
    private fun rawMetadataJson(issuerInfo: IssuerCredentialInfo): String {
        val root = safeJson.json.encodeToJsonElement(issuerInfo).jsonObject.toMutableMap()
        root["credential_configurations_supported"] = JsonObject(
            (root["credential_configurations_supported"] as JsonArray)
                .map { it.jsonObject }
                .associateBy { it["identifier"]!!.jsonPrimitive.content }
        )
        return JsonObject(root).toString()
    }

    private fun credential(selectedConfigurationId: String?) = Credential(
        id = CREDENTIAL_ID,
        format = CredentialFormat.DC_SD_JWT,
        issuerUrl = URL("https://example.com"),
        selectedConfigurationId = selectedConfigurationId,
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
}
