package ch.admin.foitt.wallet.platform.actorMetadata

import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.GetLocalizedActorImageData
import ch.admin.foitt.wallet.platform.actorMetadata.presentation.adapter.implementation.GetActorUiStateImpl
import ch.admin.foitt.wallet.platform.composables.presentation.adapter.GetDrawableFromImageData
import ch.admin.foitt.wallet.platform.locale.domain.usecase.GetLocalizedDisplay
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetActorUiStateImplTest {

    @MockK
    private lateinit var getLocalizedDisplay: GetLocalizedDisplay

    @MockK
    private lateinit var getDrawableFromImageData: GetDrawableFromImageData

    @MockK
    private lateinit var getLocalizedActorImageData: GetLocalizedActorImageData

    private lateinit var useCase: GetActorUiStateImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = GetActorUiStateImpl(getLocalizedDisplay, getDrawableFromImageData, getLocalizedActorImageData)
        coEvery { getLocalizedActorImageData(any()) } returns null
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Verana evidence reaches UI state unchanged`() = runTest {
        val evidence = VeranaTrustEvidence(
            role = VeranaTrustRole.ISSUER,
            did = "did:web:issuer.example",
            vct = "https://schemas.example/credential",
            resolution = VeranaTrustResolution(did = "did:web:issuer.example", status = VeranaTrustStatus.TRUSTED),
            accreditation = null,
        )
        val actor = ActorDisplayData.EMPTY.copy(
            name = null,
            image = null,
            nonComplianceReason = null,
            veranaTrustEvidence = evidence,
        )

        val result = useCase(actor)

        assertEquals(evidence, result.veranaTrustEvidence)
    }
}
