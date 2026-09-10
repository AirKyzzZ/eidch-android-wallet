package ch.admin.foitt.wallet.platform.appAttestation.di

import ch.admin.foitt.wallet.platform.appAttestation.domain.repository.CurrentClientAttestationRepository
import com.github.michaelbull.result.Ok
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClientAttestationHttpClientModuleTest {

    @MockK
    private lateinit var mockCurrentClientAttestationRepository: CurrentClientAttestationRepository

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { mockCurrentClientAttestationRepository.delete(any()) } returns Ok(Unit)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `A 401 ClientRequestException removes the cached client attestation`() = runTest {
        val cause = clientRequestExceptionWith(HttpStatusCode.Unauthorized)

        ClientAttestationHttpClientModule.deleteCachedClientAttestationOnUnauthorized(
            cause = cause,
            currentClientAttestationRepository = mockCurrentClientAttestationRepository,
        )

        coVerify(exactly = 1) { mockCurrentClientAttestationRepository.delete(any()) }
    }

    @Test
    fun `A non-401 ClientRequestException does not remove the cached client attestation`() = runTest {
        val cause = clientRequestExceptionWith(HttpStatusCode.Forbidden)

        ClientAttestationHttpClientModule.deleteCachedClientAttestationOnUnauthorized(
            cause = cause,
            currentClientAttestationRepository = mockCurrentClientAttestationRepository,
        )

        coVerify(exactly = 0) { mockCurrentClientAttestationRepository.delete(any()) }
    }

    @Test
    fun `A non-ClientRequestException does not remove the cached client attestation`() = runTest {
        ClientAttestationHttpClientModule.deleteCachedClientAttestationOnUnauthorized(
            cause = RuntimeException(""),
            currentClientAttestationRepository = mockCurrentClientAttestationRepository,
        )

        coVerify(exactly = 0) { mockCurrentClientAttestationRepository.delete(any()) }
    }

    private fun clientRequestExceptionWith(status: HttpStatusCode): ClientRequestException {
        val response = mockk<HttpResponse> {
            every { this@mockk.status } returns status
        }
        return mockk {
            every { this@mockk.response } returns response
        }
    }
}
