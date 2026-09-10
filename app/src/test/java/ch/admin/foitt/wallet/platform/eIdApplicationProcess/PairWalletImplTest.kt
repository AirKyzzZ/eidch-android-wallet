package ch.admin.foitt.wallet.platform.eIdApplicationProcess

import ch.admin.foitt.openid4vc.domain.model.jwe.JWEError
import ch.admin.foitt.openid4vc.domain.model.jwe.JweSizeLimits
import ch.admin.foitt.openid4vc.domain.usecase.GetHardwareKeyPair
import ch.admin.foitt.openid4vc.domain.usecase.jwe.DecryptJWEWithHardwareKey
import ch.admin.foitt.openid4vc.utils.Constants.ANDROID_KEY_STORE
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestationPoP
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.GenerateProofOfPossession
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.RequestClientAttestation
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.model.EIdRequestError
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.model.SIdChallengeResponse
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.repository.EIdRequestCaseRepository
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.repository.SIdRepository
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.usecase.AbortSIdProcess
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.usecase.implementation.PairWalletImpl
import ch.admin.foitt.wallet.platform.environmentSetup.domain.repository.EnvironmentSetupRepository
import ch.admin.foitt.wallet.platform.utils.SafeJson
import ch.admin.foitt.wallet.util.assertErr
import ch.admin.foitt.wallet.util.assertErrorType
import ch.admin.foitt.wallet.util.assertOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.PrivateKey
import kotlin.time.Duration.Companion.seconds

class PairWalletImplTest {

    @MockK
    private lateinit var mockSIdRepository: SIdRepository

    @MockK
    private lateinit var mockEIdRequestCaseRepository: EIdRequestCaseRepository

    @MockK
    private lateinit var mockRequestClientAttestation: RequestClientAttestation

    @MockK
    private lateinit var mockGenerateProofOfPossession: GenerateProofOfPossession

    @MockK
    private lateinit var mockEnvironmentSetupRepository: EnvironmentSetupRepository

    @MockK
    private lateinit var mockGetHardwareKeyPair: GetHardwareKeyPair

    @MockK
    private lateinit var mockDecryptJWEWithHardwareKey: DecryptJWEWithHardwareKey

    @MockK
    private lateinit var mockAbortSIdProcess: AbortSIdProcess

    @MockK
    private lateinit var mockKeyPair: KeyPair

    @MockK
    private lateinit var mockPrivateKey: PrivateKey

    private val safeJson = SafeJson(Json)

    private val mockSIdUrl = "https://example.com"
    private val mockSIdChallenge = SIdChallengeResponse("someChallenge")
    private val testCaseId = "testCaseId"
    private val testPairingId = "pairingId"
    private val testCredentialOfferLink = "https://example.com/offer"
    private val encryptedResponse = "someCompactJwe"

    /** The decrypted payload is the plain JSON of a PairWalletResponse. */
    private val decryptedResponse =
        """{"walletPairingId":"$testPairingId","credentialOfferLink":"$testCredentialOfferLink"}"""

    @MockK
    private lateinit var mockClientAttestation: ClientAttestation

    @MockK
    private lateinit var mockClientAttestationPoP: ClientAttestationPoP

    private lateinit var useCase: PairWalletImpl

    private lateinit var appScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        appScope = CoroutineScope(Dispatchers.Unconfined)
        useCase = PairWalletImpl(
            sIdRepository = mockSIdRepository,
            eIdRequestCaseRepository = mockEIdRequestCaseRepository,
            requestClientAttestation = mockRequestClientAttestation,
            generateProofOfPossession = mockGenerateProofOfPossession,
            environmentSetupRepository = mockEnvironmentSetupRepository,
            getHardwareKeyPair = mockGetHardwareKeyPair,
            decryptJWEWithHardwareKey = mockDecryptJWEWithHardwareKey,
            safeJson = safeJson,
            abortSIdProcess = mockAbortSIdProcess,
            appScope = appScope,
        )

        coEvery { mockAbortSIdProcess(any()) } returns Ok(Unit)

        coEvery { mockRequestClientAttestation(any(), any()) } returns Ok(mockClientAttestation)
        coEvery { mockSIdRepository.fetchChallenge() } returns Ok(mockSIdChallenge)
        coEvery {
            mockGenerateProofOfPossession.invoke(
                clientAttestation = any(),
                challenge = any(),
                audience = any(),
                requestBody = any()
            )
        } returns Ok(mockClientAttestationPoP)
        coEvery { mockEnvironmentSetupRepository.sidBackendUrl } returns mockSIdUrl
        coEvery {
            mockSIdRepository.pairWallet(
                caseId = any(),
                clientAttestation = any(),
                clientAttestationPoP = any()
            )
        } returns Ok(encryptedResponse)
        every { mockClientAttestation.keyStoreAlias } returns clientAttestationKeyAlias
        coEvery { mockGetHardwareKeyPair(any(), any()) } returns Ok(mockKeyPair)
        every { mockKeyPair.private } returns mockPrivateKey
        coEvery {
            mockDecryptJWEWithHardwareKey(any(), any(), any(), any())
        } returns Ok(decryptedResponse)
        coEvery { mockEIdRequestCaseRepository.addPairingId(any(), any()) } returns Ok(Unit)
    }

    @AfterEach
    fun tearDown() {
        appScope.cancel()
        unmockkAll()
    }

    @Test
    fun `A success follow specific steps`() = runTest {
        val result = useCase.invoke(testCaseId)
        result.assertOk()

        coVerifyOrder {
            mockRequestClientAttestation(any(), any())
            mockSIdRepository.fetchChallenge()
            mockGenerateProofOfPossession(
                clientAttestation = mockClientAttestation,
                challenge = mockSIdChallenge.challenge,
                audience = mockSIdUrl,
                requestBody = any(),
            )
            mockSIdRepository.pairWallet(
                caseId = testCaseId,
                clientAttestation = mockClientAttestation,
                clientAttestationPoP = mockClientAttestationPoP
            )
            mockGetHardwareKeyPair(any(), any())
            mockDecryptJWEWithHardwareKey(any(), any(), any(), any())
            mockEIdRequestCaseRepository.addPairingId(testCaseId, testPairingId)
        }
    }

    @Test
    fun `A success returns the fields of the decrypted response`() = runTest {
        val response = useCase.invoke(testCaseId).assertOk()

        assertEquals(testPairingId, response.walletPairingId)
        assertEquals(testCredentialOfferLink, response.credentialOfferLink)
    }

    @Test
    fun `The encrypted response is decrypted with the client attestation key`() = runTest {
        useCase.invoke(testCaseId).assertOk()

        coVerify {
            mockGetHardwareKeyPair(clientAttestationKeyAlias, ANDROID_KEY_STORE)
            mockDecryptJWEWithHardwareKey(
                jweString = encryptedResponse,
                privateKey = mockPrivateKey,
                provider = ANDROID_KEY_STORE,
                limits = JweSizeLimits.PAIRING,
            )
        }
    }

    @Test
    fun `A decrypted payload that is not valid JSON is propagated as an error`() = runTest {
        coEvery {
            mockDecryptJWEWithHardwareKey(any(), any(), any(), any())
        } returns Ok("not json")

        useCase(testCaseId).assertErrorType(EIdRequestError.Unexpected::class)
    }

    @Test
    fun `A decrypted payload missing an expected field is propagated as an error`() = runTest {
        coEvery {
            mockDecryptJWEWithHardwareKey(any(), any(), any(), any())
        } returns Ok("""{"walletPairingId":"$testPairingId"}""")

        useCase(testCaseId).assertErrorType(EIdRequestError.Unexpected::class)
    }

    @Test
    fun `A client attestation error is propagated`() = runTest {
        val exception = Exception("testException")
        coEvery { mockRequestClientAttestation(any(), any()) } returns Err(AttestationError.Unexpected(exception))

        val result = useCase("caseId")
        val error = result.assertErrorType(EIdRequestError.Unexpected::class)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `A challenge fetching failure is propagated`() = runTest {
        coEvery {
            mockSIdRepository.fetchChallenge()
        } returns Err(EIdRequestError.NetworkError)

        useCase(testCaseId).assertErrorType(EIdRequestError.NetworkError::class)
    }

    @Test
    fun `A client attestation PoP generation failure is propagated`() = runTest {
        val exception = Exception("my exception")
        coEvery {
            mockGenerateProofOfPossession(any(), any(), any(), any())
        } returns Err(AttestationError.Unexpected(exception))

        val error = useCase(testCaseId).assertErrorType(EIdRequestError.Unexpected::class)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `A pair wallet failure is propagated`() = runTest {
        val exception = Exception("my exception")
        coEvery {
            mockSIdRepository.pairWallet(
                caseId = any(),
                clientAttestation = any(),
                clientAttestationPoP = any(),
            )
        } returns Err(EIdRequestError.Unexpected(exception))

        val error = useCase(testCaseId).assertErrorType(EIdRequestError.Unexpected::class)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `A successful pairing does not abort the case`() = runTest {
        useCase(testCaseId).assertOk()

        coVerify(exactly = 0) {
            mockAbortSIdProcess(any())
        }
    }

    @Test
    fun `A decryption failure aborts the case`() = runTest {
        coEvery {
            mockDecryptJWEWithHardwareKey(any(), any(), any(), any())
        } returns Err(JWEError.Unexpected(Exception("my exception")))

        useCase(testCaseId).assertErrorType(EIdRequestError.Unexpected::class)

        coVerify(exactly = 1) {
            mockAbortSIdProcess(caseId = testCaseId)
        }
    }

    @Test
    fun `A pairing id storage failure aborts the case`() = runTest {
        coEvery {
            mockEIdRequestCaseRepository.addPairingId(any(), any())
        } returns Err(EIdRequestError.Unexpected(Exception("my exception")))

        useCase(testCaseId).assertErrorType(EIdRequestError.Unexpected::class)

        coVerify(exactly = 1) {
            mockAbortSIdProcess(caseId = testCaseId)
        }
    }

    @Test
    fun `A pair wallet failure does not abort the case, the backend has not paired anything yet`() = runTest {
        coEvery {
            mockSIdRepository.pairWallet(
                caseId = any(),
                clientAttestation = any(),
                clientAttestationPoP = any(),
            )
        } returns Err(EIdRequestError.Unexpected(Exception("my exception")))

        useCase(testCaseId).assertErrorType(EIdRequestError.Unexpected::class)

        coVerify(exactly = 0) {
            mockAbortSIdProcess(any())
        }
    }

    @Test
    fun `A failing abort does not mask the original pairing error`() = runTest {
        val exception = Exception("my exception")
        coEvery {
            mockDecryptJWEWithHardwareKey(any(), any(), any(), any())
        } returns Err(JWEError.Unexpected(exception))
        coEvery { mockAbortSIdProcess(any()) } returns Err(EIdRequestError.NetworkError)

        val error = useCase(testCaseId).assertErrorType(EIdRequestError.Unexpected::class)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `A decryption failure records no pairing id`() = runTest {
        coEvery {
            mockDecryptJWEWithHardwareKey(any(), any(), any(), any())
        } returns Err(JWEError.Unexpected(Exception("my exception")))

        useCase(testCaseId).assertErr()

        coVerify(exactly = 0) {
            mockEIdRequestCaseRepository.addPairingId(any(), any())
        }
    }

    @Test
    fun `A stuck abort does not hold up the caller`() = runTest {
        val abortStarted = CompletableDeferred<Unit>()
        coEvery {
            mockDecryptJWEWithHardwareKey(any(), any(), any(), any())
        } returns Err(JWEError.Unexpected(Exception("my exception")))
        coEvery { mockAbortSIdProcess(any()) } coAnswers {
            abortStarted.complete(Unit)
            awaitCancellation()
        }

        // The abort runs outside the caller's scope, so an abort that never comes back must not keep the
        // pairing error from reaching the caller.
        withTimeout(10.seconds) {
            useCase(testCaseId).assertErr()
        }

        assertTrue(abortStarted.isCompleted)
    }

    private companion object {
        const val clientAttestationKeyAlias = "clientAttestation"
    }
}
