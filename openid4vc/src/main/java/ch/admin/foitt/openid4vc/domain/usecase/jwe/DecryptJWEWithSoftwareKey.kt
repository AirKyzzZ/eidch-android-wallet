package ch.admin.foitt.openid4vc.domain.usecase.jwe

import ch.admin.foitt.openid4vc.domain.model.jwe.DecryptJWEError
import ch.admin.foitt.openid4vc.domain.model.jwe.JweSizeLimits
import com.github.michaelbull.result.Result
import java.security.PrivateKey

/**
 * Decrypts an ECDH-ES JWE using an extractable software private key, deriving the shared secret with the default
 * JCA provider. For a key held in the Android Keystore use [DecryptJWEWithHardwareKey] instead.
 */
interface DecryptJWEWithSoftwareKey {
    operator fun invoke(
        jweString: String,
        privateKey: PrivateKey,
        limits: JweSizeLimits,
    ): Result<String, DecryptJWEError>
}
