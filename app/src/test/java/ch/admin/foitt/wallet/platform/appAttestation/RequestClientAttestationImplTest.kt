package ch.admin.foitt.wallet.platform.appAttestation

import android.annotation.SuppressLint
import ch.admin.foitt.openid4vc.domain.model.JwkError
import ch.admin.foitt.openid4vc.domain.model.SigningAlgorithm
import ch.admin.foitt.openid4vc.domain.model.anycredential.Validity
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.JWSKeyPair
import ch.admin.foitt.openid4vc.domain.model.jwt.Jwt
import ch.admin.foitt.openid4vc.domain.model.keyBinding.KeyBindingType
import ch.admin.foitt.openid4vc.domain.usecase.CreateJwk
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AttestationChallengeResponse
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestationResponse
import ch.admin.foitt.wallet.platform.appAttestation.domain.repository.AppAttestationRepository
import ch.admin.foitt.wallet.platform.appAttestation.domain.repository.CurrentClientAttestationRepository
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.RequestClientAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.ValidateClientAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.implementation.RequestClientAttestationImpl
import ch.admin.foitt.wallet.platform.appAttestation.domain.util.getBase64CertificateChain
import ch.admin.foitt.wallet.platform.appAttestation.mock.ClientAttestationMocks
import ch.admin.foitt.wallet.platform.appAttestation.mock.KeyAttestationMocks
import ch.admin.foitt.wallet.platform.environmentSetup.domain.repository.EnvironmentSetupRepository
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.model.KeyPairError
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.CreateJWSKeyPairInHardware
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.KeySupportsPurposes
import ch.admin.foitt.wallet.util.assertErrorType
import ch.admin.foitt.wallet.util.assertOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.Signature
import java.time.Instant

class RequestClientAttestationImplTest {
    private val attestationServiceUrl = "https://attestation.example.com"
    private val challengeResponse = AttestationChallengeResponse("challenge")
    private var keyPair: KeyPair = KeyPair(mockk(), mockk())
    private val keyStoreAlias = "keyAlias"
    private val signingAlgorithm = SigningAlgorithm.ES256
    private val jwsKeyPair = JWSKeyPair(signingAlgorithm, keyPair, keyStoreAlias, KeyBindingType.SOFTWARE)
    private val jwk = KeyAttestationMocks.jwkEcP256_02
    private val clientAttestationRawJwt = ClientAttestationMocks.jwtAttestation01
    private val clientAttestationJwt = Jwt(clientAttestationRawJwt)
    private val mockClientAttestation = ClientAttestation(keyStoreAlias, clientAttestationJwt)
    private val signedChallenge = byteArrayOf(1, 2, 3)

    @MockK
    private lateinit var mockAppAttestationRepository: AppAttestationRepository

    @MockK
    private lateinit var mockEnvironmentSetupRepository: EnvironmentSetupRepository

    @MockK
    private lateinit var mockClientAttestationRepository: CurrentClientAttestationRepository

    @MockK
    private lateinit var mockCreateJWSKeyPairInHardware: CreateJWSKeyPairInHardware

    @MockK
    private lateinit var mockCreateJwk: CreateJwk

    @MockK
    private lateinit var mockValidateClientAttestation: ValidateClientAttestation

    @MockK
    private lateinit var mockSignature: Signature

    @MockK
    private lateinit var mockKeySupportsPurposes: KeySupportsPurposes

    private lateinit var useCase: RequestClientAttestation

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        useCase = RequestClientAttestationImpl(
            appAttestationRepository = mockAppAttestationRepository,
            environmentSetupRepository = mockEnvironmentSetupRepository,
            currentClientAttestationRepository = mockClientAttestationRepository,
            validateClientAttestation = mockValidateClientAttestation,
            createJWSKeyPairInHardware = mockCreateJWSKeyPairInHardware,
            createJwk = mockCreateJwk,
            keySupportsPurposes = mockKeySupportsPurposes,
        )

        coEvery { mockEnvironmentSetupRepository.defaultAttestationServiceUrl } returns attestationServiceUrl

        coEvery { mockClientAttestationRepository.delete(any()) } returns Ok(Unit)

        coEvery { mockClientAttestationRepository.get(any()) } returns Ok(null)

        coEvery { mockAppAttestationRepository.fetchChallenge(attestationServiceUrl) } returns Ok(challengeResponse)

        coEvery { mockCreateJWSKeyPairInHardware(any(), any(), any(), any(), any(), any()) } returns Ok(jwsKeyPair)

        coEvery { mockKeySupportsPurposes(any(), any(), any()) } returns true

        coEvery { mockCreateJwk(any(), any()) } returns Ok(jwk)

        mockkStatic(JWSKeyPair::getBase64CertificateChain)
        coEvery { any<JWSKeyPair>().getBase64CertificateChain() } returns Ok(listOf("base64Certificate"))

        coEvery { mockAppAttestationRepository.fetchClientAttestation(publicKey = any()) } returns Ok(
            ClientAttestationResponse(
                clientAttestationRawJwt
            )
        )

        coEvery { mockValidateClientAttestation(any(), any(), any()) } returns Ok(mockClientAttestation)
        coEvery { mockClientAttestationRepository.save(mockClientAttestation) } returns Ok(0L)
        // Mock signature instance
        mockkStatic(Signature::class)
        coEvery { Signature.getInstance(any()) } returns mockSignature
        coEvery { mockSignature.initSign(any()) } just runs
        coEvery { mockSignature.update(any<ByteArray>()) } just runs
        coEvery { mockSignature.sign() } returns signedChallenge
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @SuppressLint("CheckResult")
    @Test
    fun `A success saves a valid ClientAttestation and follows specific steps`() = runTest {
        val result = useCase(signingAlgorithm = signingAlgorithm)
        val clientAttestation = result.assertOk()

        assertEquals(mockClientAttestation, clientAttestation)

        coVerifyOrder {
            mockClientAttestationRepository.get()
            mockAppAttestationRepository.fetchChallenge(attestationServiceUrl)
            mockCreateJWSKeyPairInHardware.invoke(any(), any(), any(), any(), any(), any())
            mockClientAttestationRepository.delete(any())
            mockCreateJwk.invoke(keyPair = keyPair, any())
            mockAppAttestationRepository.fetchClientAttestation(publicKey = any())
            mockValidateClientAttestation.invoke(any(), any(), any())
            mockClientAttestationRepository.save(mockClientAttestation)
        }
    }

    @Suppress("CheckResult")
    @Test
    fun `If a valid client attestation already exists, it is directly returned`() = runTest {
        coEvery { mockClientAttestationRepository.get() } returns Ok(mockClientAttestation)

        val result = useCase(signingAlgorithm = signingAlgorithm)
        val clientAttestation = result.assertOk()

        assertEquals(mockClientAttestation, clientAttestation)

        coVerify(exactly = 1) {
            mockClientAttestationRepository.get()
        }

        coVerify(exactly = 0) {
            mockAppAttestationRepository.fetchChallenge(any())
            mockCreateJWSKeyPairInHardware.invoke(any(), any(), any(), any(), any(), any())
            mockClientAttestationRepository.delete(any())
            mockCreateJwk.invoke(keyPair = keyPair, any())
            mockAppAttestationRepository.fetchClientAttestation(publicKey = any())
            mockValidateClientAttestation.invoke(any(), any(), any())
            mockClientAttestationRepository.save(mockClientAttestation)
        }
    }

    @Test
    fun `A client attestation repository failure is propagated`() = runTest {
        val exception = Exception("myException")
        coEvery { mockClientAttestationRepository.get() } returns Err(AttestationError.Unexpected(exception))

        val result = useCase()
        val error = result.assertErrorType(AttestationError.Unexpected::class)
        assertEquals(exception, error.throwable)
    }

    @Test
    fun `An expired client attestation is replaced`() = runTest {
        val expiredClientAttestationJwt = mockk<Jwt>()
        val mockExpiredClientAttestation = ClientAttestation(keyStoreAlias, expiredClientAttestationJwt)

        coEvery { expiredClientAttestationJwt.jwtValidity } returns Validity.Expired(Instant.now())
        coEvery { mockClientAttestationRepository.get() } returns Ok(mockExpiredClientAttestation)

        val result = useCase()
        val clientAttestation = result.assertOk()

        assertEquals(mockClientAttestation, clientAttestation)

        coVerify(exactly = 1) {
            mockClientAttestationRepository.save(mockClientAttestation)
        }
    }

    @Test
    fun `A network failure while getting the challenge is propagated`() = runTest {
        coEvery { mockAppAttestationRepository.fetchChallenge(any()) } returns Err(AttestationError.NetworkError)
        val result = useCase()
        result.assertErrorType(AttestationError.NetworkError::class)

        coVerify(exactly = 1) {
            mockAppAttestationRepository.fetchChallenge(attestationServiceUrl)
        }

        coVerify(exactly = 0) {
            mockCreateJWSKeyPairInHardware(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `A JWSKeyPair creation failure is propagated`() = runTest {
        val exception = Exception("myException")
        coEvery {
            mockCreateJWSKeyPairInHardware.invoke(any(), any(), any(), any(), any(), any())
        } returns Err(KeyPairError.Unexpected(exception))
        val result = useCase()
        val error = result.assertErrorType(AttestationError.Unexpected::class)
        assertEquals(exception, error.throwable)
    }

    @Test
    fun `A client attestation deletion failure is propagated`() = runTest {
        val exception = Exception("myException")
        coEvery {
            mockClientAttestationRepository.delete(any())
        } returns Err(AttestationError.Unexpected(exception))
        val result = useCase()
        val error = result.assertErrorType(AttestationError.Unexpected::class)
        assertEquals(exception, error.throwable)
    }

    @Test
    fun `A Jwk string creation failure is propagated`() = runTest {
        val exception = Exception("myException")
        coEvery { mockCreateJwk.invoke(any(), any()) } returns Err(JwkError.Unexpected(exception))
        val result = useCase()
        val error = result.assertErrorType(AttestationError.Unexpected::class)
        assertEquals(exception, error.throwable)
    }

    @Test
    fun `A certificate creation failure is propagated`() = runTest {
        val exception = Exception("myException")
        coEvery { any<JWSKeyPair>().getBase64CertificateChain() } returns Err(exception)
        val result = useCase()
        val error = result.assertErrorType(AttestationError.Unexpected::class)
        assertEquals(exception, error.throwable)
    }

    @Test
    fun `A client attestation fetching failure is propagated`() = runTest {
        val exception = Exception("myException")
        coEvery {
            mockAppAttestationRepository.fetchClientAttestation(publicKey = any())
        } returns Err(AttestationError.Unexpected(exception))
        val result = useCase()
        val error = result.assertErrorType(AttestationError.Unexpected::class)
        assertEquals(exception, error.throwable)
    }

    @Test
    fun `A client attestation validation failure is propagated`() = runTest {
        val exception = Exception("myException")
        coEvery {
            mockValidateClientAttestation.invoke(any(), any(), any())
        } returns Err(AttestationError.Unexpected(exception))
        val result = useCase()
        val error = result.assertErrorType(AttestationError.Unexpected::class)
        assertEquals(exception, error.throwable)
    }

    @Test
    fun `A client attestation saving failure is propagated`() = runTest {
        val exception = Exception("myException")
        coEvery {
            mockClientAttestationRepository.save(any())
        } returns Err(AttestationError.Unexpected(exception))
        val result = useCase()
        val error = result.assertErrorType(AttestationError.Unexpected::class)
        assertEquals(exception, error.throwable)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Concurrent callers rotate the client attestation only once`() = runTest {
        val attestationRequested = CompletableDeferred<Unit>()
        val releaseAttestation = CompletableDeferred<Unit>()
        var storedAttestation: ClientAttestation? = null

        coEvery { mockClientAttestationRepository.get(any()) } answers { Ok(storedAttestation) }
        coEvery { mockClientAttestationRepository.save(mockClientAttestation) } answers {
            storedAttestation = mockClientAttestation
            Ok(0L)
        }
        coEvery { mockAppAttestationRepository.fetchClientAttestation(publicKey = any()) } coAnswers {
            attestationRequested.complete(Unit)
            releaseAttestation.await()
            Ok(ClientAttestationResponse(clientAttestationRawJwt))
        }

        val firstCaller = async { useCase() }
        attestationRequested.await() // the first caller now holds the lock
        val secondCaller = async { useCase() }
        runCurrent() // let the second caller pass the fast path and queue up on the lock
        releaseAttestation.complete(Unit)

        assertEquals(mockClientAttestation, firstCaller.await().assertOk())
        assertEquals(mockClientAttestation, secondCaller.await().assertOk())

        // The second caller has to pick up what the first one stored instead of rotating again,
        // which would delete the key the first caller just had attested.
        coVerify(exactly = 1) {
            mockCreateJWSKeyPairInHardware.invoke(any(), any(), any(), any(), any(), any())
            mockAppAttestationRepository.fetchClientAttestation(publicKey = any())
            mockClientAttestationRepository.save(mockClientAttestation)
        }
    }

    @Test
    fun `A failed rotation releases the lock for the next caller`() = runTest {
        coEvery { mockAppAttestationRepository.fetchChallenge(any()) } returns Err(AttestationError.NetworkError)
        useCase().assertErrorType(AttestationError.NetworkError::class)

        coEvery { mockAppAttestationRepository.fetchChallenge(attestationServiceUrl) } returns Ok(challengeResponse)
        val result = useCase()

        assertEquals(mockClientAttestation, result.assertOk())
    }
}
