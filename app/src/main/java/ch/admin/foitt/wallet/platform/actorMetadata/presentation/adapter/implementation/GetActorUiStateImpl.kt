package ch.admin.foitt.wallet.platform.actorMetadata.presentation.adapter.implementation

import androidx.compose.ui.graphics.painter.Painter
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorField
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.GetLocalizedActorImageData
import ch.admin.foitt.wallet.platform.actorMetadata.presentation.adapter.GetActorUiState
import ch.admin.foitt.wallet.platform.actorMetadata.presentation.model.ActorUiState
import ch.admin.foitt.wallet.platform.composables.presentation.adapter.GetDrawableFromImageData
import ch.admin.foitt.wallet.platform.locale.domain.usecase.GetLocalizedDisplay
import ch.admin.foitt.wallet.platform.utils.toPainter
import javax.inject.Inject

internal class GetActorUiStateImpl @Inject constructor(
    private val getLocalizedDisplay: GetLocalizedDisplay,
    private val getDrawableFromImageData: GetDrawableFromImageData,
    private val getLocalizedActorImageData: GetLocalizedActorImageData,
) : GetActorUiState {
    override suspend fun invoke(
        actorDisplayData: ActorDisplayData,
    ): ActorUiState {
        val actorName: String? = actorDisplayData.getLocalizedName()
        val actorDataLogo: Painter? = actorDisplayData.getLocalizedIcon()
        val localizedNonComplianceReason = actorDisplayData.getLocalizedReason()

        return ActorUiState(
            name = actorName,
            painter = actorDataLogo,
            trustStatus = actorDisplayData.trustStatus,
            vcSchemaTrustStatus = actorDisplayData.vcSchemaTrustStatus,
            actorType = actorDisplayData.actorType,
            actorComplianceState = actorDisplayData.actorComplianceState,
            nonComplianceReason = localizedNonComplianceReason,
            veranaTrustEvidence = actorDisplayData.veranaTrustEvidence,
        )
    }

    private fun ActorDisplayData.getLocalizedName(): String? = name?.let {
        getLocalizedDisplay(
            displays = name,
            preferredLocaleString = preferredLanguage,
        )
    }?.let {
            actorField: ActorField<String> ->
        actorField.value
    }

    private suspend fun ActorDisplayData.getLocalizedIcon(): Painter? =
        getLocalizedActorImageData(this)?.let {
            getDrawableFromImageData(it)?.toPainter()
        }

    private fun ActorDisplayData.getLocalizedReason(): String? = nonComplianceReason?.let {
        getLocalizedDisplay(
            displays = nonComplianceReason,
            preferredLocaleString = preferredLanguage,
        )?.value
    }
}
