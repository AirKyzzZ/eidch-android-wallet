package ch.admin.foitt.wallet.feature.presentationRequest.presentation.model

import ch.admin.foitt.wallet.platform.actorMetadata.presentation.model.ActorUiState

data class PresentationDeclinedUiState(
    val verifierUiState: ActorUiState,
    val canReport: Boolean,
) {
    companion object {
        val EMPTY = PresentationDeclinedUiState(
            verifierUiState = ActorUiState.EMPTY,
            canReport = false,
        )
    }
}
