package ch.admin.foitt.wallet.platform.appAttestation

import android.annotation.SuppressLint
import ch.admin.foitt.openid4vc.domain.model.SigningAlgorithm
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.JWSKeyPair
import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.openid4vc.domain.model.jwt.Jwt
import ch.admin.foitt.openid4vc.domain.model.keyBinding.KeyBindingType
import ch.admin.foitt.openid4vc.domain.usecase.CreateJwk
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AttestationChallengeResponse
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.KeyAttestationJwt
import ch.admin.foitt.wallet.platform.appAttestation.domain.repository.AppAttestationRepository
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.GetAttestationUrlFromDid
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.RequestBatchKeyAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.ValidateKeyAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.implementation.RequestBatchKeyAttestationImpl
import ch.admin.foitt.wallet.platform.appAttestation.domain.util.getBase64CertificateChain
import ch.admin.foitt.wallet.platform.appAttestation.mock.KeyAttestationMocks
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.model.KeyPairError
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.CreateJWSKeyPairInHardware
import ch.admin.foitt.wallet.platform.ssi.domain.usecase.DeleteKeyStoreEntry
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
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPair

class RequestBatchKeyAttestationImplTest {

    @MockK
    private lateinit var mockGetAttestationUrlFromDid: GetAttestationUrlFromDid

    @MockK
    private lateinit var mockAppAttestationRepository: AppAttestationRepository

    @MockK
    private lateinit var mockCreateJWSKeyPairInHardware: CreateJWSKeyPairInHardware

    @MockK
    private lateinit var mockCreateJwk: CreateJwk

    @MockK
    private lateinit var mockValidateKeyAttestation: ValidateKeyAttestation

    @MockK
    private lateinit var mockDeleteKeyStoreEntry: DeleteKeyStoreEntry

    private val challengeResponse = AttestationChallengeResponse("challenge")

    private val keyPair: KeyPair = KeyPair(mockk(), mockk())
    private val signingAlgorithm = SigningAlgorithm.ES256
    private val jwsKeyPairs = KEY_ALIASES.map { keyAlias ->
        JWSKeyPair(signingAlgorithm, keyPair, keyAlias, KeyBindingType.HARDWARE)
    }

    private val attestationJwt = Jwt(KeyAttestationMocks.jwtSimple01.value)

    private val actorDid = "actor did"
    private val attestationServiceUrl = "attestation service url"

    private lateinit var useCase: RequestBatchKeyAttestation

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        useCase = RequestBatchKeyAttestationImpl(
            getAttestationUrlFromDid = mockGetAttestationUrlFromDid,
            attestationRepository = mockAppAttestationRepository,
            createJWSKeyPairInHardware = mockCreateJWSKeyPairInHardware,
            createJwk = mockCreateJwk,
            validateKeyAttestation = mockValidateKeyAttestation,
            deleteKeyStoreEntry = mockDeleteKeyStoreEntry,
        )

        every { mockGetAttestationUrlFromDid(actorDid) } returns Ok(attestationServiceUrl)

        coEvery { mockAppAttestationRepository.fetchChallenge(attestationServiceUrl) } returns Ok(challengeResponse)

        coEvery {
            mockCreateJWSKeyPairInHardware(any(), any(), any(), any(), any())
        } returnsMany jwsKeyPairs.map { jwsKeyPair -> Ok(jwsKeyPair) }

        coEvery { mockCreateJwk(any(), any()) } returns Ok(KeyAttestationMocks.jwkEcP256_01)

        mockkStatic(JWSKeyPair::getBase64CertificateChain)
        coEvery { any<JWSKeyPair>().getBase64CertificateChain() } returns Ok(listOf("base64Certificate"))

        coEvery {
            mockAppAttestationRepository.fetchBatchKeyAttestation(attestationServiceUrl, any())
        } returns Ok(List(AMOUNT) { KeyAttestationMocks.jwtSimple01 })

        coEvery { mockValidateKeyAttestation(any(), any()) } returns Ok(attestationJwt)

        coEvery { mockDeleteKeyStoreEntry(any()) } just runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @SuppressLint("CheckResult")
    @Test
    fun `A success attests all keys against a single challenge in one request`() = runTest {
        val result = useCase(amount = AMOUNT, actorDid = actorDid, signingAlgorithm = signingAlgorithm).assertOk()

        assertEquals(AMOUNT, result.size)
        assertEquals(jwsKeyPairs, result.map { keyAttestation -> keyAttestation.keyPair })
        assertEquals(List(AMOUNT) { attestationJwt }, result.map { keyAttestation -> keyAttestation.attestation })

        coVerifyOrder {
            mockGetAttestationUrlFromDid(actorDid)
            mockAppAttestationRepository.fetchChallenge(attestationServiceUrl)
            mockCreateJWSKeyPairInHardware(any(), any(), any(), any(), any())
            mockAppAttestationRepository.fetchBatchKeyAttestation(attestationServiceUrl, any())
            mockValidateKeyAttestation(any(), any())
        }

        verify(exactly = 1) { mockGetAttestationUrlFromDid(actorDid) }
        coVerify(exactly = 1) { mockAppAttestationRepository.fetchChallenge(attestationServiceUrl) }
        coVerify(exactly = 1) { mockAppAttestationRepository.fetchBatchKeyAttestation(attestationServiceUrl, any()) }
        coVerify(exactly = 0) { mockDeleteKeyStoreEntry(any()) }
    }

    @SuppressLint("CheckResult")
    @Test
    fun `All key pairs of a batch are created with the same challenge`() = runTest {
        val challenges = mutableListOf<ByteArray?>()

        useCase(amount = AMOUNT, actorDid = actorDid, signingAlgorithm = signingAlgorithm).assertOk()

        coVerify(exactly = AMOUNT) {
            mockCreateJWSKeyPairInHardware(any(), any(), any(), any(), captureNullable(challenges))
        }
        assertEquals(
            List(AMOUNT) { challengeResponse.challenge },
            challenges.map { challenge -> challenge?.decodeToString() },
        )
    }

    @SuppressLint("CheckResult")
    @Test
    fun `Each key is validated against the attestation the repository returned for it`() = runTest {
        coEvery { mockCreateJwk(any(), any()) } returnsMany listOf(
            Ok(KeyAttestationMocks.jwkEcP256_01),
            Ok(KeyAttestationMocks.jwkEcP256_02),
        )
        // the repository returns one attestation per key, in the order the keys were sent in
        coEvery { mockAppAttestationRepository.fetchBatchKeyAttestation(any(), any()) } returns Ok(
            listOf(ATTESTATION_JWT_01, ATTESTATION_JWT_02)
        )

        useCase(amount = 2, actorDid = actorDid, signingAlgorithm = signingAlgorithm).assertOk()

        val originalJwks = mutableListOf<Jwk>()
        val keyAttestationJwts = mutableListOf<KeyAttestationJwt>()
        coVerify(exactly = 2) {
            mockValidateKeyAttestation(capture(originalJwks), capture(keyAttestationJwts))
        }

        assertEquals(JWK_01_X, originalJwks[0].x)
        assertEquals(ATTESTATION_JWT_01, keyAttestationJwts[0])
        assertEquals(JWK_02_X, originalJwks[1].x)
        assertEquals(ATTESTATION_JWT_02, keyAttestationJwts[1])
    }

    @SuppressLint("CheckResult")
    @Test
    fun `The public keys are sent in the order they were created in`() = runTest {
        coEvery { mockCreateJwk(any(), any()) } returnsMany listOf(
            Ok(KeyAttestationMocks.jwkEcP256_01),
            Ok(KeyAttestationMocks.jwkEcP256_02),
        )
        coEvery { mockAppAttestationRepository.fetchBatchKeyAttestation(any(), any()) } returns Ok(
            listOf(ATTESTATION_JWT_01, ATTESTATION_JWT_02)
        )

        useCase(amount = 2, actorDid = actorDid, signingAlgorithm = signingAlgorithm).assertOk()

        val publicKeys = mutableListOf<List<Jwk>>()
        coVerify(exactly = 1) {
            mockAppAttestationRepository.fetchBatchKeyAttestation(attestationServiceUrl, capture(publicKeys))
        }

        assertEquals(listOf(JWK_01_X, JWK_02_X), publicKeys.single().map { jwk -> jwk.x })
    }

    @SuppressLint("CheckResult")
    @Test
    fun `Every invocation fetches a new challenge`() = runTest {
        useCase(amount = AMOUNT, actorDid = actorDid, signingAlgorithm = signingAlgorithm).assertOk()
        useCase(amount = AMOUNT, actorDid = actorDid, signingAlgorithm = signingAlgorithm)

        coVerify(exactly = 2) { mockAppAttestationRepository.fetchChallenge(attestationServiceUrl) }
    }

    @Test
    fun `An unresolvable attestation service url fails the batch and contacts no attestation service`() = runTest {
        every {
            mockGetAttestationUrlFromDid(actorDid)
        } returns Err(AttestationError.Unexpected(IllegalStateException("did error")))

        useCase(amount = AMOUNT, actorDid = actorDid, signingAlgorithm = signingAlgorithm).assertErr()

        coVerify(exactly = 0) {
            mockAppAttestationRepository.fetchChallenge(any())
            mockCreateJWSKeyPairInHardware(any(), any(), any(), any(), any())
            mockDeleteKeyStoreEntry(any())
        }
    }

    @Test
    fun `A challenge failure is propagated and creates no key pair`() = runTest {
        coEvery { mockAppAttestationRepository.fetchChallenge(any()) } returns Err(AttestationError.NetworkError)

        useCase(amount = AMOUNT, actorDid = actorDid, signingAlgorithm = signingAlgorithm)
            .assertErrorType(AttestationError.NetworkError::class)

        coVerify(exactly = 0) {
            mockCreateJWSKeyPairInHardware(any(), any(), any(), any(), any())
            mockDeleteKeyStoreEntry(any())
        }
    }

    @Test
    fun `A batch fetching failure deletes every key pair that was already created`() = runTest {
        val exception = Exception("myException")
        coEvery {
            mockAppAttestationRepository.fetchBatchKeyAttestation(any(), any())
        } returns Err(AttestationError.Unexpected(exception))

        val error = useCase(amount = AMOUNT, actorDid = actorDid, signingAlgorithm = signingAlgorithm)
            .assertErrorType(AttestationError.Unexpected::class)
        assertEquals(exception, error.throwable)

        assertAllKeyPairsDeleted()
    }

    @Test
    fun `An attestation validation failure deletes every key pair of the batch`() = runTest {
        val exception = Exception("myException")
        coEvery { mockValidateKeyAttestation(any(), any()) } returns Err(AttestationError.Unexpected(exception))

        val error = useCase(amount = AMOUNT, actorDid = actorDid, signingAlgorithm = signingAlgorithm)
            .assertErrorType(AttestationError.Unexpected::class)
        assertEquals(exception, error.throwable)

        assertAllKeyPairsDeleted()
    }

    @Test
    fun `A key pair creation failure deletes the key pairs created before it and sends no batch request`() = runTest {
        coEvery { mockCreateJWSKeyPairInHardware(any(), any(), any(), any(), any()) } returnsMany listOf(
            Ok(jwsKeyPairs[0]),
            Err(KeyPairError.Unexpected(Exception("myException"))),
        )

        useCase(amount = AMOUNT, actorDid = actorDid, signingAlgorithm = signingAlgorithm)
            .assertErrorType(AttestationError.Unexpected::class)

        coVerify(exactly = 1) { mockDeleteKeyStoreEntry(jwsKeyPairs[0].keyId) }
        coVerify(exactly = 0) { mockAppAttestationRepository.fetchBatchKeyAttestation(any(), any()) }
    }

    @Test
    fun `A batch the repository could not correlate fails the whole batch and validates nothing`() = runTest {
        coEvery { mockAppAttestationRepository.fetchBatchKeyAttestation(any(), any()) } returns Err(
            AttestationError.ValidationError("Batch key attestation ids do not match the requested ids")
        )

        useCase(amount = AMOUNT, actorDid = actorDid, signingAlgorithm = signingAlgorithm)
            .assertErrorType(AttestationError.ValidationError::class)

        coVerify(exactly = 0) { mockValidateKeyAttestation(any(), any()) }
        assertAllKeyPairsDeleted()
    }

    private fun assertAllKeyPairsDeleted() {
        coVerify(exactly = 1) {
            jwsKeyPairs.forEach { jwsKeyPair -> mockDeleteKeyStoreEntry(jwsKeyPair.keyId) }
        }
    }

    private companion object {
        const val AMOUNT = 3
        val KEY_ALIASES = listOf("keyAlias1", "keyAlias2", "keyAlias3")

        val ATTESTATION_JWT_01 = KeyAttestationMocks.jwtAttestation01
        val ATTESTATION_JWT_02 = KeyAttestationMocks.jwtAttestation02NoStorage

        const val JWK_01_X = "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc"
        const val JWK_02_X = "MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4"
    }
}
