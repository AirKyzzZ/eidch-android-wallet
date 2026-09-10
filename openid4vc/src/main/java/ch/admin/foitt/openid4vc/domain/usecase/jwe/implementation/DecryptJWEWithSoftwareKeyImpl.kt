package ch.admin.foitt.openid4vc.domain.usecase.jwe.implementation

import ch.admin.foitt.openid4vc.domain.model.jwe.DecryptJWEError
import ch.admin.foitt.openid4vc.domain.model.jwe.JweSizeLimits
import ch.admin.foitt.openid4vc.domain.usecase.jwe.DecryptJWEWithSoftwareKey
import com.github.michaelbull.result.Result
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.jwk.ECKey
import java.security.PrivateKey
import javax.inject.Inject

internal class DecryptJWEWithSoftwareKeyImpl @Inject constructor() : DecryptJWEWithSoftwareKey {
    override fun invoke(
        jweString: String,
        privateKey: PrivateKey,
        limits: JweSizeLimits,
    ): Result<String, DecryptJWEError> = decryptJwe(
        jweString = jweString,
        limits = limits,
    ) { ephemeralPublicKey ->
        val ecKey = ECKey.Builder(ephemeralPublicKey)
            .privateKey(privateKey)
            .build()

        ECDHDecrypter(ecKey)
    }
}
