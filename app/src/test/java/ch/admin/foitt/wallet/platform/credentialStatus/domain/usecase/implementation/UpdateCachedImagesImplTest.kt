package ch.admin.foitt.wallet.platform.credentialStatus.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.credentialStatus.domain.usecase.UpdateCachedImages
import ch.admin.foitt.wallet.platform.ssi.domain.model.SsiError
import ch.admin.foitt.wallet.platform.ssi.domain.repository.CredentialOfferRepository
import ch.admin.foitt.wallet.platform.ssi.domain.repository.DeferredCredentialRepository
import ch.admin.foitt.wallet.platform.ssi.domain.repository.VerifiableCredentialRepository
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

class UpdateCachedImagesImplTest {

    @MockK
    private lateinit var mockVerifiableCredentialRepository: VerifiableCredentialRepository

    @MockK
    private lateinit var mockDeferredCredentialRepository: DeferredCredentialRepository

    @MockK
    private lateinit var mockCredentialOfferRepository: CredentialOfferRepository

    private lateinit var useCase: UpdateCachedImages

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        useCase = UpdateCachedImagesImpl(
            verifiableCredentialRepository = mockVerifiableCredentialRepository,
            credentialOfferRepository = mockCredentialOfferRepository,
            deferredCredentialRepository = mockDeferredCredentialRepository,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Caching all images refreshes images for all credentials`() = runTest {
        val credentialIds = listOf(1L, 2L, 3L)
        coEvery { mockVerifiableCredentialRepository.getAllIds() } returns Ok(credentialIds)
        coEvery { mockDeferredCredentialRepository.getAllIds() } returns Ok(emptyList())
        coEvery { mockCredentialOfferRepository.refreshCachedImages(any()) } returns Ok(Unit)

        useCase()

        coVerify(exactly = 1) { mockVerifiableCredentialRepository.getAllIds() }
        credentialIds.forEach { id ->
            coVerify(exactly = 1) { mockCredentialOfferRepository.refreshCachedImages(id) }
        }
    }

    @Test
    fun `Caching images when getting all ids fails silently fails`() = runTest {
        coEvery { mockVerifiableCredentialRepository.getAllIds() } returns Err(SsiError.Unexpected(Exception()))
        coEvery { mockDeferredCredentialRepository.getAllIds() } returns Ok(emptyList())

        useCase()

        coVerify(exactly = 1) { mockVerifiableCredentialRepository.getAllIds() }
        coVerify(exactly = 0) { mockCredentialOfferRepository.refreshCachedImages(any()) }
    }

    @Test
    fun `Caching images continues even if refreshing one credential fails`() = runTest {
        val credentialIds = listOf(1L, 2L, 3L)
        coEvery { mockVerifiableCredentialRepository.getAllIds() } returns Ok(credentialIds)
        coEvery { mockDeferredCredentialRepository.getAllIds() } returns Ok(emptyList())
        coEvery { mockCredentialOfferRepository.refreshCachedImages(1L) } returns Err(Exception())
        coEvery { mockCredentialOfferRepository.refreshCachedImages(2L) } returns Ok(Unit)
        coEvery { mockCredentialOfferRepository.refreshCachedImages(3L) } returns Ok(Unit)

        useCase()

        coVerify(exactly = 1) { mockVerifiableCredentialRepository.getAllIds() }
        credentialIds.forEach { id ->
            coVerify(exactly = 1) { mockCredentialOfferRepository.refreshCachedImages(id) }
        }
    }

    @Test
    fun `Caching all images refreshes images for verifiable and deferred credentials`() = runTest {
        val verifiableIds = listOf(1L, 2L)
        val deferredIds = listOf(3L, 4L)
        coEvery { mockVerifiableCredentialRepository.getAllIds() } returns Ok(verifiableIds)
        coEvery { mockDeferredCredentialRepository.getAllIds() } returns Ok(deferredIds)
        coEvery { mockCredentialOfferRepository.refreshCachedImages(any()) } returns Ok(Unit)

        useCase()

        coVerify(exactly = 1) { mockVerifiableCredentialRepository.getAllIds() }
        coVerify(exactly = 1) { mockDeferredCredentialRepository.getAllIds() }
        (verifiableIds + deferredIds).forEach { id ->
            coVerify(exactly = 1) { mockCredentialOfferRepository.refreshCachedImages(id) }
        }
    }
}
