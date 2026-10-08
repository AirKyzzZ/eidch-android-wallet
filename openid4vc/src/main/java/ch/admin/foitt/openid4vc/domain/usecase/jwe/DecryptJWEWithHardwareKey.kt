package ch.admin.foitt.openid4vc.domain.usecase.jwe

import ch.admin.foitt.openid4vc.domain.model.jwe.DecryptJWEError
import ch.admin.foitt.openid4vc.domain.model.jwe.JweSizeLimits
import com.github.michaelbull.result.Result
import java.security.PrivateKey

/**
 * Decrypts an ECDH-ES JWE using a non-extractable Android Keystore private key.
 */
interface DecryptJWEWithHardwareKey {
    suspend operator fun invoke(
        jweString: String,
        privateKey: PrivateKey,
        provider: String,
        limits: JweSizeLimits,
    ): Result<String, DecryptJWEError>
}
