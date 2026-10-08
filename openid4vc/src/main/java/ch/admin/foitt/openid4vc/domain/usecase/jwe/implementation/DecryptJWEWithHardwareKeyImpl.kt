package ch.admin.foitt.openid4vc.domain.usecase.jwe.implementation

import ch.admin.foitt.openid4vc.di.DefaultDispatcher
import ch.admin.foitt.openid4vc.domain.model.jwe.DecryptJWEError
import ch.admin.foitt.openid4vc.domain.model.jwe.JweSizeLimits
import ch.admin.foitt.openid4vc.domain.usecase.jwe.DecryptJWEWithHardwareKey
import com.github.michaelbull.result.Result
import com.nimbusds.jose.crypto.ECDHDecrypter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.PrivateKey
import java.security.Security
import javax.inject.Inject

internal class DecryptJWEWithHardwareKeyImpl @Inject constructor(
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : DecryptJWEWithHardwareKey {
    override suspend fun invoke(
        jweString: String,
        privateKey: PrivateKey,
        provider: String,
        limits: JweSizeLimits,
    ): Result<String, DecryptJWEError> = withContext(defaultDispatcher) {
        decryptJwe(
            jweString = jweString,
            limits = limits,
        ) { ephemeralPublicKey ->
            ECDHDecrypter(privateKey, null, ephemeralPublicKey.curve).apply {
                // The key agreement has to happen inside the keystore, the private key is only a handle. Content
                // decryption stays on the default provider since the derived key is ordinary key material.
                jcaContext.keyEncryptionProvider = checkNotNull(Security.getProvider(provider)) {
                    "Provider $provider is not available"
                }
            }
        }
    }
}
