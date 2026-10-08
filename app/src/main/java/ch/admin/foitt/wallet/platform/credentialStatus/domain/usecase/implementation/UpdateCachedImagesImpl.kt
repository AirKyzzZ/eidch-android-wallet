package ch.admin.foitt.wallet.platform.credentialStatus.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.credentialStatus.domain.usecase.UpdateCachedImages
import ch.admin.foitt.wallet.platform.ssi.domain.repository.CredentialOfferRepository
import ch.admin.foitt.wallet.platform.ssi.domain.repository.DeferredCredentialRepository
import ch.admin.foitt.wallet.platform.ssi.domain.repository.VerifiableCredentialRepository
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onErr
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject

class UpdateCachedImagesImpl @Inject constructor(
    private val verifiableCredentialRepository: VerifiableCredentialRepository,
    private val deferredCredentialRepository: DeferredCredentialRepository,
    private val credentialOfferRepository: CredentialOfferRepository,
) : UpdateCachedImages {
    override suspend fun invoke() = coroutineScope {
        val verifiableIds = verifiableCredentialRepository.getAllIds().onErr {
            Timber.e("Failed to get all verifiable credential ids: $it")
        }.getOrElse { emptyList() }
        val deferredIds = deferredCredentialRepository.getAllIds().onErr {
            Timber.e("Failed to get all deferred credential ids: $it")
        }.getOrElse { emptyList() }

        val allIds = (verifiableIds + deferredIds).toSet()

        val semaphore = Semaphore(MAX_CONCURRENCY)
        allIds.forEach { id ->
            launch {
                semaphore.withPermit {
                    credentialOfferRepository.refreshCachedImages(id).onErr { error ->
                        Timber.e(error, "Failed to refresh cached images for credential $id")
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_CONCURRENCY = 4
    }
}
