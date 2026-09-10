package ch.admin.foitt.openid4vc.domain.usecase.jwe.implementation

import ch.admin.foitt.openid4vc.domain.model.jwe.DecryptJWEError
import ch.admin.foitt.openid4vc.domain.model.jwe.JWEError
import ch.admin.foitt.openid4vc.domain.model.jwe.JweSizeLimits
import ch.admin.foitt.openid4vc.domain.model.jwe.PayloadSizeExceededException
import ch.admin.foitt.openid4vc.domain.model.jwe.UnsupportedJweHeaderException
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import com.nimbusds.jose.JWEDecrypter
import com.nimbusds.jose.JWEDecrypterOption
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.crypto.opts.MaxCompressedCipherTextLength
import com.nimbusds.jose.jwk.ECKey
import timber.log.Timber

/**
 * This should be the single place a received JWE is decrypted.
 * After parsing, this also checks the header against defined SwissProfile constraints.
 *
 * @param jweString the JWE in compact serialization, as received.
 * @param limits the bounds on what the payload may inflate to while being decrypted.
 * @param createDecrypter builds the decrypter for the validated ephemeral public key of the header.
 */
internal fun decryptJwe(
    jweString: String,
    limits: JweSizeLimits,
    createDecrypter: (ephemeralPublicKey: ECKey) -> JWEDecrypter,
): Result<String, DecryptJWEError> = runSuspendCatching {
    val jwe = JWEObject.parse(jweString)
    val ephemeralPublicKey = jwe.header.checkSwissProfile()

    jwe.decrypt(
        createDecrypter(ephemeralPublicKey),
        setOf<JWEDecrypterOption>(MaxCompressedCipherTextLength(limits.maxCompressedCipherTextLength))
    )

    val payloadBytes = jwe.payload.toBytes()
    if (payloadBytes.size > limits.maxDecompressedPayloadSize) {
        throw PayloadSizeExceededException(
            "Decompressed JWE payload exceeds limit: ${payloadBytes.size} > ${limits.maxDecompressedPayloadSize}"
        )
    }

    jwe.payload.toString()
}.mapError { throwable ->
    Timber.e(t = throwable, message = "Error during JWE decryption")
    when (throwable) {
        is UnsupportedJweHeaderException -> JWEError.UnsupportedHeader(throwable.message.orEmpty())
        is PayloadSizeExceededException -> JWEError.PayloadSizeExceeded
        else -> JWEError.Unexpected(throwable)
    }
}
