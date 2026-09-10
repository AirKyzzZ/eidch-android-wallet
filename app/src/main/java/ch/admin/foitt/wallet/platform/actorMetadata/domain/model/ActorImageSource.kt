package ch.admin.foitt.wallet.platform.actorMetadata.domain.model

import ch.admin.foitt.wallet.platform.database.domain.model.LocalizedDisplay

/**
 * A per-locale actor image candidate, carrying whichever sources exist for that locale:
 * the cached image [data] blob (once backfilled) and/or the [uri] (until then).
 */
internal data class ActorImageSource(
    override val locale: String,
    val data: ByteArray?,
    val uri: String?,
) : LocalizedDisplay
