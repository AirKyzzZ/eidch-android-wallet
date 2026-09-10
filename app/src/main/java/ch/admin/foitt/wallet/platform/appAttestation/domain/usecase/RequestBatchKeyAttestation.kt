package ch.admin.foitt.wallet.platform.appAttestation.domain.usecase

import androidx.annotation.CheckResult
import ch.admin.foitt.openid4vc.domain.model.KeyStorageSecurityLevel
import ch.admin.foitt.openid4vc.domain.model.SigningAlgorithm
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.KeyAttestation
import ch.admin.foitt.wallet.platform.appAttestation.domain.model.RequestKeyAttestationError
import com.github.michaelbull.result.Result

interface RequestBatchKeyAttestation {
    /**
     * Creates [amount] hardware backed key pairs and attests all them.
     *
     * The batch is atomic: if any step fails, no key attestation is returned and every key pair that was already
     * created is removed from the key store again.
     */
    @CheckResult
    suspend operator fun invoke(
        amount: Int,
        actorDid: String?,
        signingAlgorithm: SigningAlgorithm = KeyAttestation.signingAlgorithm,
        keyStorageSecurityLevels: List<KeyStorageSecurityLevel>? = null,
    ): Result<List<KeyAttestation>, RequestKeyAttestationError>
}
