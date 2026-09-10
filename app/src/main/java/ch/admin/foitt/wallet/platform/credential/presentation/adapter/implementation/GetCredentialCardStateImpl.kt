package ch.admin.foitt.wallet.platform.credential.presentation.adapter.implementation

import androidx.compose.ui.graphics.Color
import ch.admin.foitt.wallet.platform.actorEnvironment.domain.model.ActorEnvironment
import ch.admin.foitt.wallet.platform.composables.presentation.adapter.GetColor
import ch.admin.foitt.wallet.platform.composables.presentation.adapter.GetContrastedColor
import ch.admin.foitt.wallet.platform.composables.presentation.adapter.GetDrawableFromImageData
import ch.admin.foitt.wallet.platform.credential.domain.model.CredentialDisplayData
import ch.admin.foitt.wallet.platform.credential.domain.model.DeferredCredentialDisplayData
import ch.admin.foitt.wallet.platform.credential.presentation.adapter.GetCredentialCardState
import ch.admin.foitt.wallet.platform.credential.presentation.model.CredentialCardState
import ch.admin.foitt.wallet.platform.utils.toPainter
import javax.inject.Inject

internal class GetCredentialCardStateImpl @Inject constructor(
    private val getColor: GetColor,
    private val getContrastedColor: GetContrastedColor,
    private val getDrawableFromImageData: GetDrawableFromImageData
) : GetCredentialCardState {

    override suspend fun invoke(credentialDisplayData: CredentialDisplayData): CredentialCardState {
        val backgroundColor: Color = getBackgroundColor(credentialDisplayData.backgroundColor)

        return CredentialCardState(
            credentialId = credentialDisplayData.credentialId,
            title = credentialDisplayData.title,
            subtitle = credentialDisplayData.subtitle,
            status = credentialDisplayData.status,
            borderColor = backgroundColor,
            backgroundColor = backgroundColor,
            contentColor = getContrastedColor(backgroundColor),
            logo = getDrawableFromImageData(credentialDisplayData.logo)?.toPainter(),
            isCredentialFromBetaIssuer = credentialDisplayData.actorEnvironment == ActorEnvironment.BETA,
            progressionState = credentialDisplayData.progressionState,
        )
    }

    override suspend fun invoke(credentialDisplayData: DeferredCredentialDisplayData): CredentialCardState {
        val backgroundColor: Color = getBackgroundColor(credentialDisplayData.backgroundColor)

        return CredentialCardState(
            credentialId = credentialDisplayData.credentialId,
            title = credentialDisplayData.title,
            subtitle = null,
            status = null,
            borderColor = backgroundColor,
            backgroundColor = backgroundColor,
            contentColor = getContrastedColor(backgroundColor),
            logo = getDrawableFromImageData(credentialDisplayData.logo)?.toPainter(),
            isCredentialFromBetaIssuer = false,
            deferredStatus = credentialDisplayData.status,
        )
    }

    private fun getBackgroundColor(colorString: String?): Color {
        // We do not support alpha channel on background color
        if (colorString.isNullOrEmpty() || !colorString.startsWith("#") || colorString.length > 7) {
            return CredentialCardState.defaultCardColor
        }

        return getColor(colorString) ?: CredentialCardState.defaultCardColor
    }
}
