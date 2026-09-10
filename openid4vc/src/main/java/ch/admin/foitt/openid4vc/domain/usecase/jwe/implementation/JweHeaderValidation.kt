package ch.admin.foitt.openid4vc.domain.usecase.jwe.implementation

import ch.admin.foitt.openid4vc.domain.model.jwe.UnsupportedJweHeaderException
import com.nimbusds.jose.CompressionAlgorithm
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey

/**
 * The Swiss Profile, the only JOSE profile the wallet accepts on a JWE it receives. Nimbus is far more permissive
 * on its own: it accepts every ECDH-ES key wrapping variant and every content encryption method it implements, so
 * the profile has to be enforced here.
 */
private object SwissProfile {
    val ALGORITHMS = setOf(JWEAlgorithm.ECDH_ES)
    val CURVES = setOf(Curve.P_256)

    val ENCRYPTION_METHODS = setOf(EncryptionMethod.A256GCM, EncryptionMethod.A128GCM)

    val COMPRESSION_ALGORITHMS = setOf(CompressionAlgorithm.DEF)
}

/**
 * Checks the header of a received JWE against the [SwissProfile], before any decryption is attempted.
 *
 * @return the ephemeral public key, guaranteed to be an EC key on an accepted curve.
 * @throws UnsupportedJweHeaderException if the header is outside the profile.
 */
internal fun JWEHeader.checkSwissProfile(): ECKey {
    val ephemeralKey = ephemeralPublicKey as? ECKey
        ?: throw UnsupportedJweHeaderException("missing or non-EC ephemeral public key")
    val compression = compressionAlgorithm

    val reason = when {
        algorithm !in SwissProfile.ALGORITHMS -> "unsupported alg: $algorithm"
        encryptionMethod !in SwissProfile.ENCRYPTION_METHODS -> "unsupported enc: $encryptionMethod"
        compression != null && compression !in SwissProfile.COMPRESSION_ALGORITHMS -> "unsupported zip: $compression"
        ephemeralKey.curve !in SwissProfile.CURVES -> "unsupported ephemeral key curve: ${ephemeralKey.curve}"
        else -> null
    }

    if (reason != null) {
        throw UnsupportedJweHeaderException(reason)
    }

    return ephemeralKey
}
