package ch.admin.foitt.wallet.platform.keyPairGenerator.domain.usecase

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ch.admin.foitt.openid4vc.domain.model.SigningAlgorithm
import ch.admin.foitt.wallet.platform.keyPairGenerator.domain.model.CreateKeyGenSpecError
import com.github.michaelbull.result.Result

interface CreateKeyGenSpec {
    operator fun invoke(
        keyId: String,
        signingAlgorithm: SigningAlgorithm,
        useStrongBox: Boolean,
        attestationChallenge: ByteArray?,
        purposes: Int = KeyProperties.PURPOSE_SIGN,
    ): Result<KeyGenParameterSpec, CreateKeyGenSpecError>
}
