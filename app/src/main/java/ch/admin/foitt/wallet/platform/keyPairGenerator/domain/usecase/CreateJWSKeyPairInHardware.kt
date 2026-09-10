package ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase

import android.security.keystore.KeyProperties
import androidx.annotation.CheckResult
import ch.admin.foitt.openid4vc.domain.model.KeyStorageSecurityLevel
import ch.admin.foitt.openid4vc.domain.model.SigningAlgorithm
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.JWSKeyPair
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.model.CreateJWSKeyPairError
import com.github.michaelbull.result.Result

interface CreateJWSKeyPairInHardware {
    @CheckResult
    suspend operator fun invoke(
        keyAlias: String? = null,
        signingAlgorithm: SigningAlgorithm,
        provider: String,
        keyStorageSecurityLevels: List<KeyStorageSecurityLevel>? = null,
        attestationChallenge: ByteArray?,
        purposes: Int = KeyProperties.PURPOSE_SIGN,
    ): Result<JWSKeyPair, CreateJWSKeyPairError>
}
