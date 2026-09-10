package ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase

import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorDisplayData

interface GetLocalizedActorImageData {
    suspend operator fun invoke(
        actorDisplayData: ActorDisplayData,
    ): ByteArray?
}
