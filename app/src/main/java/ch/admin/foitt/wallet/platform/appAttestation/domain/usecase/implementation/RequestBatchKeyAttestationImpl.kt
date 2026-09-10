package ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.CreateJwkError
import ch.admin.foitt.openid4vc.domain.model.KeyStorageSecurityLevel
import ch.admin.foitt.openid4vc.domain.model.SigningAlgorithm
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.JWSKeyPair
import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.openid4vc.domain.usecase.CreateJwk
import ch.admin.foitt.openid4vc.utils.Constants
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AppAttestationRepositoryError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AttestableKey
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.GetAttestationUrlFromDidError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.KeyAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.RequestKeyAttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ValidateKeyAttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.toRequestKeyAttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.repository.AppAttestationRepository
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.GetAttestationUrlFromDid
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.RequestBatchKeyAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.ValidateKeyAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.util.getBase64CertificateChain
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.model.CreateJWSKeyPairError
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.CreateJWSKeyPairInHardware
import ch.admin.foitt.wallet.platform.ssi.domain.usecase.DeleteKeyStoreEntry
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onErr
import javax.inject.Inject

class RequestBatchKeyAttestationImpl @Inject constructor(
    private val getAttestationUrlFromDid: GetAttestationUrlFromDid,
    private val attestationRepository: AppAttestationRepository,
    private val createJWSKeyPairInHardware: CreateJWSKeyPairInHardware,
    private val createJwk: CreateJwk,
    private val validateKeyAttestation: ValidateKeyAttestation,
    private val deleteKeyStoreEntry: DeleteKeyStoreEntry,
) : RequestBatchKeyAttestation {

    override suspend operator fun invoke(
        amount: Int,
        actorDid: String?,
        signingAlgorithm: SigningAlgorithm,
        keyStorageSecurityLevels: List<KeyStorageSecurityLevel>?,
    ): Result<List<KeyAttestation>, RequestKeyAttestationError> = coroutineBinding {
        val attestationServiceUrl = getAttestationUrlFromDid(actorDid)
            .mapError(GetAttestationUrlFromDidError::toRequestKeyAttestationError)
            .bind()
        val challengeResponse = attestationRepository.fetchChallenge(attestationServiceUrl)
            .mapError(AppAttestationRepositoryError::toRequestKeyAttestationError)
            .bind()

        val attestableKeys = createAttestableKeys(
            amount = amount,
            challenge = challengeResponse.challenge,
            signingAlgorithm = signingAlgorithm,
            keyStorageSecurityLevels = keyStorageSecurityLevels,
        ).bind()

        attestKeys(
            attestationServiceUrl = attestationServiceUrl,
            attestableKeys = attestableKeys,
        ).onErr {
            // the batch is atomic, no partially attested key pair is kept
            attestableKeys.forEach { attestableKey -> deleteKeyStoreEntry(attestableKey.keyPair.keyId) }
        }.bind()
    }

    private suspend fun attestKeys(
        attestationServiceUrl: String,
        attestableKeys: List<AttestableKey>,
    ): Result<List<KeyAttestation>, RequestKeyAttestationError> = coroutineBinding {
        val keyAttestationJwts = attestationRepository.fetchBatchKeyAttestation(
            url = attestationServiceUrl,
            publicKeys = attestableKeys.map { attestableKey -> attestableKey.jwk },
        ).mapError(AppAttestationRepositoryError::toRequestKeyAttestationError).bind()

        attestableKeys.zip(keyAttestationJwts) { attestableKey, keyAttestationJwt ->
            val attestedJwt = validateKeyAttestation(
                originalJwk = attestableKey.jwk,
                keyAttestationJwt = keyAttestationJwt,
            ).mapError(ValidateKeyAttestationError::toRequestKeyAttestationError).bind()

            KeyAttestation(
                keyPair = attestableKey.keyPair,
                attestation = attestedJwt,
            )
        }
    }

    private suspend fun createAttestableKeys(
        amount: Int,
        challenge: String,
        signingAlgorithm: SigningAlgorithm,
        keyStorageSecurityLevels: List<KeyStorageSecurityLevel>?,
    ): Result<List<AttestableKey>, RequestKeyAttestationError> {
        val attestableKeys = mutableListOf<AttestableKey>()
        return coroutineBinding<List<AttestableKey>, RequestKeyAttestationError> {
            repeat(amount) {
                attestableKeys += createAttestableKey(
                    challenge = challenge,
                    signingAlgorithm = signingAlgorithm,
                    keyStorageSecurityLevels = keyStorageSecurityLevels,
                ).bind()
            }
            attestableKeys
        }.onErr {
            // the batch is atomic, no key pair of an incomplete batch is kept
            attestableKeys.forEach { attestableKey -> deleteKeyStoreEntry(attestableKey.keyPair.keyId) }
        }
    }

    private suspend fun createAttestableKey(
        challenge: String,
        signingAlgorithm: SigningAlgorithm,
        keyStorageSecurityLevels: List<KeyStorageSecurityLevel>?,
    ): Result<AttestableKey, RequestKeyAttestationError> = coroutineBinding {
        val keyPair: JWSKeyPair = createJWSKeyPairInHardware(
            keyAlias = null,
            signingAlgorithm = signingAlgorithm,
            provider = Constants.ANDROID_KEY_STORE,
            keyStorageSecurityLevels = keyStorageSecurityLevels,
            attestationChallenge = challenge.encodeToByteArray(),
        ).mapError(CreateJWSKeyPairError::toRequestKeyAttestationError).bind()

        toAttestableKey(keyPair)
            .onErr { deleteKeyStoreEntry(keyPair.keyId) }
            .bind()
    }

    private suspend fun toAttestableKey(
        keyPair: JWSKeyPair,
    ): Result<AttestableKey, RequestKeyAttestationError> = coroutineBinding {
        val keyJwkString = createJwk(
            keyPair = keyPair.keyPair,
            algorithm = keyPair.algorithm,
        ).mapError(CreateJwkError::toRequestKeyAttestationError).bind()

        val certificateChainBase64 = keyPair.getBase64CertificateChain()
            .mapError { error ->
                error.toRequestKeyAttestationError("RequestBatchKeyAttestation certificate chain failed")
            }.bind()

        val keyJwk = Jwk.fromEcKey(keyJwkString, certificateChainBase64)
            .mapError { error ->
                error.toRequestKeyAttestationError("RequestBatchKeyAttestation Jwk creation failed")
            }.bind()

        AttestableKey(keyPair = keyPair, jwk = keyJwk)
    }
}
