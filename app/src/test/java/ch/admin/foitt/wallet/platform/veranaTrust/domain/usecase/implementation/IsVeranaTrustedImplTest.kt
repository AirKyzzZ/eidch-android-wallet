package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.ResolveVeranaTrust
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IsVeranaTrustedImplTest {

    @MockK
    private lateinit var resolveVeranaTrust: ResolveVeranaTrust

    private lateinit var useCase: IsVeranaTrustedImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = IsVeranaTrustedImpl(resolveVeranaTrust)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a DID Verana resolves TRUSTED is trusted`() = runTest {
        coEvery { resolveVeranaTrust(DID) } returns resolution(VeranaTrustStatus.TRUSTED)

        assertTrue(useCase(DID))
    }

    @Test
    fun `a DID Verana resolves UNTRUSTED is not trusted`() = runTest {
        coEvery { resolveVeranaTrust(DID) } returns resolution(VeranaTrustStatus.UNTRUSTED)

        assertFalse(useCase(DID))
    }

    @Test
    fun `a DID Verana cannot resolve is not trusted`() = runTest {
        coEvery { resolveVeranaTrust(DID) } returns resolution(VeranaTrustStatus.UNVERIFIED)

        assertFalse(useCase(DID))
    }

    @Test
    fun `a failing resolution is not trusted`() = runTest {
        coEvery { resolveVeranaTrust(DID) } throws IllegalStateException("indexer down")

        assertFalse(useCase(DID))
    }

    private fun resolution(status: VeranaTrustStatus) = VeranaTrustResolution(did = DID, status = status)

    private companion object {
        const val DID = "did:webvh:QmService:service.example"
    }
}
