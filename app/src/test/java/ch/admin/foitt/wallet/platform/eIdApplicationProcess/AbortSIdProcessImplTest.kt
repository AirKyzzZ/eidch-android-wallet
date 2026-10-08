package ch.admin.foitt.wallet.platform.eIdApplicationProcess

import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestationPoP
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.GenerateProofOfPossession
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.RequestClientAttestation
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.model.EIdRequestError
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.model.SIdChallengeResponse
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.repository.SIdRepository
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.usecase.implementation.AbortSIdProcessImpl
import ch.admin.foitt.wallet.platform.environmentSetup.domain.repository.EnvironmentSetupRepository
import ch.admin.foitt.wallet.util.assertErr
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AbortSIdProcessImplTest {

    @MockK
    private lateinit var mockSIdRepository: SIdRepository

    @MockK
    private lateinit var mockRequestClientAttestation: RequestClientAttestation

    @MockK
    private lateinit var mockGenerateProofOfPossession: GenerateProofOfPossession

    @MockK
    private lateinit var mockEnvironmentSetupRepository: EnvironmentSetupRepository

    @MockK
    private lateinit var mockClientAttestation: ClientAttestation

    @MockK
    private lateinit var mockClientAttestationPoP: ClientAttestationPoP

    private val mockSIdUrl = "https://example.com"
    private val mockSIdChallenge = SIdChallengeResponse("someChallenge")
    private val testCaseId = "testCaseId"

    private lateinit var useCase: AbortSIdProcessImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = AbortSIdProcessImpl(
            sIdRepository = mockSIdRepository,
            requestClientAttestation = mockRequestClientAttestation,
            generateProofOfPossession = mockGenerateProofOfPossession,
            environmentSetupRepository = mockEnvironmentSetupRepository,
        )

        coEvery { mockRequestClientAttestation(any(), any()) } returns Ok(mockClientAttestation)
        coEvery { mockSIdRepository.fetchChallenge() } returns Ok(mockSIdChallenge)
        coEvery { mockEnvironmentSetupRepository.sidBackendUrl } returns mockSIdUrl
        coEvery {
            mockGenerateProofOfPossession(
                clientAttestation = any(),
                challenge = any(),
                audience = any(),
                requestBody = any(),
            )
        } returns Ok(mockClientAttestationPoP)
        coEvery {
            mockSIdRepository.abortSIdProcess(
                caseId = any(),
                clientAttestation = any(),
                clientAttestationPoP = any(),
            )
        } returns Ok(Unit)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Without an attestation to sign with, no abort is sent`() = runTest {
        coEvery {
            mockRequestClientAttestation(any(), any())
        } returns Err(AttestationError.Unexpected(Exception("my exception")))

        useCase(testCaseId).assertErr()

        coVerify(exactly = 0) {
            mockSIdRepository.abortSIdProcess(any(), any(), any())
        }
    }

    @Test
    fun `Failing the challenge fails the abort`() = runTest {
        coEvery { mockSIdRepository.fetchChallenge() } returns Err(EIdRequestError.NetworkError)

        useCase(testCaseId).assertErr()

        coVerify(exactly = 0) {
            mockSIdRepository.abortSIdProcess(any(), any(), any())
        }
    }

    @Test
    fun `Failing PoP fails the abort`() = runTest {
        coEvery {
            mockGenerateProofOfPossession(
                clientAttestation = any(),
                challenge = any(),
                audience = any(),
                requestBody = any(),
            )
        } returns Err(AttestationError.Unexpected(Exception("my exception")))

        useCase(testCaseId).assertErr()

        coVerify(exactly = 0) {
            mockSIdRepository.abortSIdProcess(any(), any(), any())
        }
    }
}
