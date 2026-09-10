package ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.GetKeyPairError
import ch.admin.foitt.openid4vc.domain.model.jwe.DecryptJWEError
import ch.admin.foitt.openid4vc.domain.model.jwe.JweSizeLimits
import ch.admin.foitt.openid4vc.domain.usecase.GetHardwareKeyPair
import ch.admin.foitt.openid4vc.domain.usecase.jwe.DecryptJWEWithHardwareKey
import ch.admin.foitt.openid4vc.utils.Constants
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestationPoP
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.GenerateProofOfPossessionError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.RequestClientAttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.GenerateProofOfPossession
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.RequestClientAttestation
import ch.admin.foitt.wallet.platform.di.IoDispatcherScope
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.model.EIdRequestCaseRepositoryError
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.model.PairWalletError
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.model.PairWalletResponse
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.model.SIdRepositoryError
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.model.toPairWalletError
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.repository.EIdRequestCaseRepository
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.repository.SIdRepository
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.usecase.AbortSIdProcess
import ch.admin.foitt.wallet.platform.eIdApplicationProcess.domain.usecase.PairWallet
import ch.admin.foitt.wallet.platform.environmentSetup.domain.repository.EnvironmentSetupRepository
import ch.admin.foitt.wallet.platform.utils.JsonParsingError
import ch.admin.foitt.wallet.platform.utils.SafeJson
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onErr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import javax.inject.Inject

class PairWalletImpl @Inject constructor(
    private val sIdRepository: SIdRepository,
    private val eIdRequestCaseRepository: EIdRequestCaseRepository,
    private val requestClientAttestation: RequestClientAttestation,
    private val generateProofOfPossession: GenerateProofOfPossession,
    private val environmentSetupRepository: EnvironmentSetupRepository,
    private val getHardwareKeyPair: GetHardwareKeyPair,
    private val decryptJWEWithHardwareKey: DecryptJWEWithHardwareKey,
    private val safeJson: SafeJson,
    private val abortSIdProcess: AbortSIdProcess,
    @param:IoDispatcherScope private val appScope: CoroutineScope,
) : PairWallet {
    override suspend operator fun invoke(caseId: String) = coroutineBinding {
        val clientAttestation = requestClientAttestation()
            .mapError(RequestClientAttestationError::toPairWalletError).bind()

        val challengeResponse = sIdRepository.fetchChallenge()
            .mapError(SIdRepositoryError::toPairWalletError).bind()

        val clientAttestationProofOfPossession: ClientAttestationPoP = generateProofOfPossession(
            clientAttestation = clientAttestation,
            challenge = challengeResponse.challenge,
            audience = environmentSetupRepository.sidBackendUrl,
            requestBody = JsonObject(emptyMap()),
        ).mapError(GenerateProofOfPossessionError::toPairWalletError).bind()

        val encryptedResponse = sIdRepository.pairWallet(
            caseId = caseId,
            clientAttestation = clientAttestation,
            clientAttestationPoP = clientAttestationProofOfPossession,
        ).bind()

        // The backend has paired this wallet from here on. A pairing id the wallet fails to record is
        // indistinguishable from a foreign wallet pairing to the case, and VerifyWalletPairing aborts the
        // e-ID request over it later on. So the case gets aborted right away instead of being left behind.
        recordPairing(
            caseId = caseId,
            clientAttestation = clientAttestation,
            encryptedResponse = encryptedResponse,
        ).onErr { error ->
            Timber.e("Recording the pairing of case $caseId failed, aborting the case: $error")
            appScope.launch {
                abortSIdProcess(caseId = caseId)
                    .onErr { error -> Timber.e("Aborting case $caseId after an unrecorded pairing failed: $error") }
            }
        }.bind()
    }

    private suspend fun recordPairing(
        caseId: String,
        clientAttestation: ClientAttestation,
        encryptedResponse: String,
    ): Result<PairWalletResponse, PairWalletError> = coroutineBinding {
        val keyPair = getHardwareKeyPair(
            keyId = clientAttestation.keyStoreAlias,
            provider = Constants.ANDROID_KEY_STORE,
        ).mapError(GetKeyPairError::toPairWalletError).bind()

        val decryptedResponse = decryptJWEWithHardwareKey(
            jweString = encryptedResponse,
            privateKey = keyPair.private,
            provider = Constants.ANDROID_KEY_STORE,
            limits = JweSizeLimits.PAIRING,
        ).mapError(DecryptJWEError::toPairWalletError).bind()

        val response = safeJson.safeDecodeStringTo<PairWalletResponse>(decryptedResponse)
            .mapError(JsonParsingError::toPairWalletError).bind()

        eIdRequestCaseRepository.addPairingId(caseId, response.walletPairingId)
            .mapError(EIdRequestCaseRepositoryError::toPairWalletError).bind()

        response
    }
}
