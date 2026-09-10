package ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorImageSource
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.GetLocalizedActorImageData
import ch.admin.foitt.wallet.platform.locale.domain.usecase.GetLocalizedDisplay
import ch.admin.foitt.wallet.platform.utils.domain.usecase.GetImageDataFromUri
import javax.inject.Inject

internal class GetLocalizedActorImageDataImpl @Inject constructor(
    private val getLocalizedDisplay: GetLocalizedDisplay,
    private val getImageDataFromUri: GetImageDataFromUri,
) : GetLocalizedActorImageData {
    override suspend fun invoke(actorDisplayData: ActorDisplayData): ByteArray? {
        val dataByLocale = actorDisplayData.imageData?.associateBy { it.locale }.orEmpty()
        val uriByLocale = actorDisplayData.image?.associateBy { it.locale }.orEmpty()

        // The blob may be backfilled for some locales and not others, so resolve the locale
        // over the union of both sources; the representation must not decide which locale wins.
        val candidates = (dataByLocale.keys + uriByLocale.keys).map { locale ->
            ActorImageSource(
                locale = locale,
                data = dataByLocale[locale]?.value,
                uri = uriByLocale[locale]?.value,
            )
        }
        if (candidates.isEmpty()) return null

        val chosen = getLocalizedDisplay(candidates, actorDisplayData.preferredLanguage)
        return chosen?.data ?: chosen?.uri?.let { getImageDataFromUri(it) }
    }
}
