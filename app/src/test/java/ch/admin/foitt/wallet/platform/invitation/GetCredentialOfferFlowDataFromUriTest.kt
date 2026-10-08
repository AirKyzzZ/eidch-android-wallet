package ch.admin.foitt.wallet.platform.invitation

import android.webkit.URLUtil
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.CredentialOffer
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.Grant
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.PreAuthorizedContent
import ch.admin.foitt.wallet.platform.credential.domain.usecase.implementation.mock.MockFetchCredential.CREDENTIAL_ISSUER
import ch.admin.foitt.wallet.platform.invitation.domain.model.InvitationError
import ch.admin.foitt.wallet.platform.invitation.domain.usecase.FetchCredentialOfferByReference
import ch.admin.foitt.wallet.platform.invitation.domain.usecase.GetCredentialOfferFromUri
import ch.admin.foitt.wallet.platform.invitation.domain.usecase.implementation.GetCredentialOfferFromUriImpl
import ch.admin.foitt.wallet.util.SafeJsonTestInstance.safeJson
import ch.admin.foitt.wallet.util.assertErrorType
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI

class GetCredentialOfferFlowDataFromUriTest {
    lateinit var getCredentialOfferUseCase: GetCredentialOfferFromUri

    @BeforeEach
    fun setup() {
        mockkStatic(URLUtil::class)
        every { URLUtil.isHttpsUrl(any()) } returns true
        getCredentialOfferUseCase = GetCredentialOfferFromUriImpl(
            safeJson = safeJson,
            fetchCredentialOfferByReference = FetchCredentialOfferByReference {
                error("Inline credential offers must not perform a network request")
            },
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `valid VC invitation should return a CredentialOffer`() = runTest {
        val input = VALID_URI
        val expected = Ok(
            CredentialOffer(
                credentialIssuer = CREDENTIAL_ISSUER,
                credentialConfigurationIds = listOf("testcred"),
                grants = Grant(
                    preAuthorizedCode = PreAuthorizedContent(preAuthorizedCode = "test"),
                    authorizedCode = null
                )
            )
        )
        assertEquals(expected, getCredentialOfferUseCase(uri = input))
    }

    @Test
    fun `invalid VC invitation should return an error`() = runTest {
        assertTrue(
            getCredentialOfferUseCase(uri = URI("")).getError() is InvitationError.CredentialOfferDeserializationFailed,
            "empty input should return an error"
        )

        // Known schema, no valid query
        assertTrue(
            getCredentialOfferUseCase(
                uri = URI("openid-credential-offer://foo")
            ).getError() is InvitationError.CredentialOfferDeserializationFailed,
            "input without a query should return an error"
        )

        // Known schema, no valid query
        assertTrue(
            getCredentialOfferUseCase(
                uri = URI("openid-credential-offer://?foo")
            ).getError() is InvitationError.CredentialOfferDeserializationFailed,
            "input without a valid query should return an error"
        )

        // Known schema, no valid query
        assertTrue(
            getCredentialOfferUseCase(
                uri = URI("openid-credential-offer://?foo=")
            ).getError() is InvitationError.CredentialOfferDeserializationFailed,
            "input without a valid query should return an error"
        )

        // Known schema, valid query but no credential offer
        assertTrue(
            getCredentialOfferUseCase(
                uri = URI("openid-credential-offer://?foo=bar")
            ).getError() is InvitationError.CredentialOfferDeserializationFailed,
            "input without a valid query should return an error"
        )

        // Known schema, valid query and credential offer but unsupported grant type
        assertTrue(
            getCredentialOfferUseCase(
                uri = URI(
                    "openid-credential-offer://?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Fissuer.example.com%22%2C%22credential_configuration_ids%22%3A%5B%22testcred%22%5D%2C%22grants%22%3A%7B%7D%7D"
                )
            ).getError() is InvitationError.UnsupportedGrantType,
            "input with unsupported grant type should return an error"
        )
    }

    @Test
    fun `Getting credential offer maps json parsing errors`() = runTest {
        val result = getCredentialOfferUseCase(INVALID_JSON_URI)
        result.assertErrorType(InvitationError.CredentialOfferDeserializationFailed::class)
    }

    @Test
    fun `credential offer uri fetches and parses the referenced offer`() = runTest {
        val referencedOffer = """
            {
              "credential_issuer": "https://issuer.example.com",
              "credential_configuration_ids": ["testcred"],
              "grants": {
                "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                  "pre-authorized_code": "fresh-secret"
                }
              }
            }
        """.trimIndent()
        var fetchedUri: URI? = null
        val fetcher = FetchCredentialOfferByReference { uri ->
            fetchedUri = uri
            Ok(referencedOffer)
        }
        val useCase = GetCredentialOfferFromUriImpl(safeJson, fetcher)

        val result = useCase(
            URI(
                "openid-credential-offer://?credential_offer_uri=" +
                    "https%3A%2F%2Fissuer.example.com%2Foffers%2Ftrusted"
            )
        )

        assertEquals(
            CredentialOffer(
                credentialIssuer = CREDENTIAL_ISSUER,
                credentialConfigurationIds = listOf("testcred"),
                grants = Grant(
                    preAuthorizedCode = PreAuthorizedContent(preAuthorizedCode = "fresh-secret"),
                    authorizedCode = null,
                ),
            ),
            result.component1(),
        )
        assertEquals(URI("https://issuer.example.com/offers/trusted"), fetchedUri)
    }

    @Test
    fun `credential offer uri rejects ambiguous or unsafe references`() = runTest {
        val unsafeUris = listOf(
            "openid-credential-offer://?credential_offer_uri=http%3A%2F%2Fissuer.example.com%2Foffer",
            "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fuser%40issuer.example.com%2Foffer",
            "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example.com%2Foffer%23fragment",
            "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example.com%2Fone" +
                "&credential_offer_uri=https%3A%2F%2Fissuer.example.com%2Ftwo",
            "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example.com%2Foffer" +
                "&credential_offer=%7B%7D",
        )

        unsafeUris.forEach { uri ->
            assertTrue(
                getCredentialOfferUseCase(URI(uri)).getError() is InvitationError.CredentialOfferDeserializationFailed,
                "unsafe credential offer reference should be rejected: $uri",
            )
        }
    }

    companion object {
        private val VALID_URI = URI(
            "openid-credential-offer://?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Fissuer.example.com%22%2C%22credential_configuration_ids%22%3A%5B%22testcred%22%5D%2C%22grants%22%3A%7B%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%22test%22%7D%7D%7D"
        )

        private val INVALID_JSON_URI = URI(
            "openid-credential-offer://?credential_offer=invalidJson"
        )
    }
}
