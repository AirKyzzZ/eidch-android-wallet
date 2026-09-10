package ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.CreateJwkError
import ch.admin.foitt.openid4vc.domain.model.SigningAlgorithm
import ch.admin.foitt.openid4vc.domain.model.anycredential.Validity
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.JWSKeyPair
import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.openid4vc.domain.usecase.CreateJwk
import ch.admin.foitt.openid4vc.utils.Constants
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.AppAttestationRepositoryError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ClientAttestationRepositoryError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.RequestClientAttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.ValidateClientAttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.toRequestClientAttestationError
import ch.admin.foitt.wallet.platform.appAttestation.domain.repository.AppAttestationRepository
import ch.admin.foitt.wallet.platform.appAttestation.domain.repository.CurrentClientAttestationRepository
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.RequestClientAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.usecase.ValidateClientAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.util.getBase64CertificateChain
import ch.admin.foitt.wallet.platform.environmentSetup.domain.repository.EnvironmentSetupRepository
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.model.CreateJWSKeyPairError
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.CreateJWSKeyPairInHardware
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase.KeySupportsPurposes
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

class RequestClientAttestationImpl @Inject constructor(
    private val appAttestationRepository: AppAttestationRepository,
    private val environmentSetupRepository: EnvironmentSetupRepository,
    private val currentClientAttestationRepository: CurrentClientAttestationRepository,
    private val validateClientAttestation: ValidateClientAttestation,
    private val createJWSKeyPairInHardware: CreateJWSKeyPairInHardware,
    private val createJwk: CreateJwk,
    private val keySupportsPurposes: KeySupportsPurposes,
) : RequestClientAttestation {
    override suspend operator fun invoke(
        keyAlias: String,
        signingAlgorithm: SigningAlgorithm,
    ): Result<ClientAttestation, RequestClientAttestationError> = coroutineBinding {
        rotationLock.withLock {
            getUsableAttestation(keyAlias).bind()?.let { return@withLock it }

            rotateAndAttest(keyAlias, signingAlgorithm).bind()
        }
    }

    /**
     * Returns the stored attestation when it can still be used as is, or null when it has to be rotated.
     */
    private suspend fun getUsableAttestation(
        keyAlias: String,
    ): Result<ClientAttestation?, RequestClientAttestationError> = coroutineBinding {
        val currentClientAttestation = currentClientAttestationRepository.get(
            keyPairAlias = keyAlias
        ).mapError(ClientAttestationRepositoryError::toRequestClientAttestationError).bind()

        if (currentClientAttestation == null || currentClientAttestation.attestation.jwtValidity is Validity.Expired) {
            return@coroutineBinding null
        }

        val keyIsUsable = keySupportsPurposes(
            keyAlias = currentClientAttestation.keyStoreAlias,
            provider = Constants.ANDROID_KEY_STORE,
            purposes = ClientAttestation.KEY_PURPOSES,
        )
        if (!keyIsUsable) {
            Timber.d("Client attestation key lacks the required purposes, will regenerate it")
        }

        currentClientAttestation.takeIf { keyIsUsable }
    }

    private suspend fun rotateAndAttest(
        keyAlias: String,
        signingAlgorithm: SigningAlgorithm,
    ): Result<ClientAttestation, RequestClientAttestationError> = coroutineBinding {
        val challengeResponse = appAttestationRepository
            .fetchChallenge(environmentSetupRepository.defaultAttestationServiceUrl)
            .mapError(AppAttestationRepositoryError::toRequestClientAttestationError)
            .bind()

        val keyPair: JWSKeyPair = createJWSKeyPairInHardware(
            keyAlias = keyAlias,
            signingAlgorithm = signingAlgorithm,
            provider = Constants.ANDROID_KEY_STORE,
            attestationChallenge = challengeResponse.challenge.encodeToByteArray(),
            purposes = ClientAttestation.KEY_PURPOSES,
        ).mapError(CreateJWSKeyPairError::toRequestClientAttestationError).bind()

        currentClientAttestationRepository.delete(keyAlias)
            .mapError(ClientAttestationRepositoryError::toRequestClientAttestationError).bind()

        val keyJwkString = createJwk(
            keyPair = keyPair.keyPair,
            algorithm = keyPair.algorithm,
        ).mapError(CreateJwkError::toRequestClientAttestationError).bind()

        val certificateChainBase64 = keyPair.getBase64CertificateChain()
            .mapError { throwable ->
                throwable.toRequestClientAttestationError("RequestClientAttestation certificate chain failed")
            }.bind()

        val keyJwk = Jwk.fromEcKey(keyJwkString, certificateChainBase64)
            .mapError { throwable ->
                throwable.toRequestClientAttestationError("RequestClientAttestation Jwk creation failed")
            }.bind()

        val clientAttestationJwt = appAttestationRepository.fetchClientAttestation(
            publicKey = keyJwk,
        ).mapError(AppAttestationRepositoryError::toRequestClientAttestationError).bind()

        val clientAttestation = validateClientAttestation(
            keyStoreAlias = keyPair.keyId,
            originalJwk = keyJwk,
            clientAttestationResponse = clientAttestationJwt,
        ).mapError(ValidateClientAttestationError::toRequestClientAttestationError).bind()

        currentClientAttestationRepository.save(clientAttestation)
            .mapError(ClientAttestationRepositoryError::toRequestClientAttestationError).bind()

        clientAttestation
    }

    private companion object {
        /**
         * Serializes rotations across the whole process. A rotation replaces the keystore entry and the
         * database row behind a single fixed alias, so two concurrent rotations destroy each other's key.
         * The lock has to be static because this use case is an unscoped binding and gets instantiated
         * once per injection point.
         */
        val rotationLock = Mutex()
    }
}
